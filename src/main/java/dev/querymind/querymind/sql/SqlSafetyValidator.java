package dev.querymind.querymind.sql;

import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.update.Update;
import org.springframework.stereotype.Component;

/**
 * Checks a piece of SQL before we let it run.
 *
 * We parse the SQL into a syntax tree (an "AST") and look at what the statement
 * actually is. This is far safer than searching the text for words like "DROP",
 * because those words can hide inside comments, strings or column names. We ask
 * "is this a DROP statement?", not "does the text contain the word DROP?".
 *
 * The rules:
 *   - Only one statement at a time (blocks tricks like "SELECT ...; DROP ...").
 *   - A SELECT is always fine (it only reads data).
 *   - If the connection is read-only, nothing except SELECT is allowed.
 *   - DELETE or UPDATE without a WHERE clause is blocked (would hit every row).
 *   - DROP, TRUNCATE, ALTER, CREATE and anything else is blocked.
 */
@Component
public class SqlSafetyValidator {

    public ValidationResult validate(String sql, boolean readOnly) {
        Statement statement;
        try {
            // Statements is a list of the parsed statements. We want exactly one.
            Statements statements = CCJSqlParserUtil.parseStatements(sql);
            if (statements.size() != 1) {
                return ValidationResult.block("Only one SQL statement is allowed at a time.");
            }
            statement = statements.get(0);
        } catch (Exception e) {
            return ValidationResult.block("The SQL could not be understood (failed to parse).");
        }

        // Reads are always allowed.
        if (statement instanceof Select) {
            return ValidationResult.allow();
        }

        // Everything past here changes data or structure.
        if (readOnly) {
            return ValidationResult.block(
                    "This connection is read-only, so only SELECT queries are allowed.");
        }

        if (statement instanceof Insert) {
            return ValidationResult.allow();
        }
        if (statement instanceof Update update) {
            if (update.getWhere() == null) {
                return ValidationResult.block("UPDATE without a WHERE clause is not allowed.");
            }
            return ValidationResult.allow();
        }
        if (statement instanceof Delete delete) {
            if (delete.getWhere() == null) {
                return ValidationResult.block("DELETE without a WHERE clause is not allowed.");
            }
            return ValidationResult.allow();
        }

        // DROP, TRUNCATE, ALTER, CREATE, and anything we didn't explicitly allow.
        return ValidationResult.block(
                "That kind of statement is not allowed (for example DROP, TRUNCATE or ALTER).");
    }
}
