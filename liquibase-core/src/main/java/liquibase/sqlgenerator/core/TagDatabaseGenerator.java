package liquibase.sqlgenerator.core;

import liquibase.action.Action;
import liquibase.action.core.UnparsedSql;
import liquibase.exception.UnsupportedException;
import liquibase.statement.core.UpdateDataStatement;
import liquibase.statementlogic.StatementLogicChain;
import liquibase.statementlogic.StatementLogicFactory;
import liquibase.database.Database;
import liquibase.database.core.InformixDatabase;
import liquibase.database.core.MySQLDatabase;
import liquibase.exception.ValidationErrors;
import  liquibase.ExecutionEnvironment;
import liquibase.statement.core.TagDatabaseStatement;

public class TagDatabaseGenerator extends AbstractSqlGenerator<TagDatabaseStatement> {

    @Override
    public ValidationErrors validate(TagDatabaseStatement tagDatabaseStatement, ExecutionEnvironment env, StatementLogicChain chain) {
        ValidationErrors validationErrors = new ValidationErrors();
        validationErrors.checkRequiredField("tag", tagDatabaseStatement.getTag());
        return validationErrors;
    }

    @Override
    public Action[] generateActions(TagDatabaseStatement statement, ExecutionEnvironment env, StatementLogicChain sqlGeneratorChain) throws UnsupportedException {
        String liquibaseSchema = null;
        Database database = env.getTargetDatabase();
   		liquibaseSchema = database.getLiquibaseSchemaName();
        UpdateDataStatement updateDataStatement = new UpdateDataStatement(database.getLiquibaseCatalogName(), liquibaseSchema, database.getDatabaseChangeLogTableName());
        updateDataStatement.addNewColumnValue("TAG", statement.getTag());
        if (database instanceof MySQLDatabase) {
            try {
                long version = Long.parseLong(database.getDatabaseProductVersion().substring(0, 1));

                if (version < 5) {
                    return new Action[]{
                            new UnparsedSql("UPDATE "+ database.escapeTableName(database.getLiquibaseCatalogName(), database.getLiquibaseSchemaName(), database.getDatabaseChangeLogTableName())+" C LEFT JOIN (SELECT MAX(DATEEXECUTED) as MAXDATE FROM (SELECT DATEEXECUTED FROM `DATABASECHANGELOG`) AS X) D ON C.DATEEXECUTED = D.MAXDATE SET C.TAG = '" + statement.getTag() + "' WHERE D.MAXDATE IS NOT NULL")
                    };
                }

            } catch (Throwable e) {
                ; //assume it is version 5
            }
            updateDataStatement.setWhere("DATEEXECUTED = (SELECT MAX(DATEEXECUTED) FROM (SELECT DATEEXECUTED FROM " + database.escapeTableName(database.getLiquibaseCatalogName(), liquibaseSchema, database.getDatabaseChangeLogTableName()) + ") AS X)");
        } else if (database instanceof InformixDatabase) {
            return new Action[]{
                    new UnparsedSql("SELECT MAX(dateexecuted) max_date FROM " + database.escapeTableName(database.getLiquibaseCatalogName(), liquibaseSchema, database.getDatabaseChangeLogTableName()) + " INTO TEMP max_date_temp WITH NO LOG"),
                    new UnparsedSql("UPDATE "+ database.escapeTableName(database.getLiquibaseCatalogName(), liquibaseSchema, database.getDatabaseChangeLogTableName())+" SET TAG = '"+statement.getTag()+"' WHERE DATEEXECUTED = (SELECT max_date FROM max_date_temp);"),
                    new UnparsedSql("DROP TABLE max_date_temp;")
            };
        } else {
            updateDataStatement.setWhere("DATEEXECUTED = (SELECT MAX(DATEEXECUTED) FROM " + database.escapeTableName(database.getLiquibaseCatalogName(), liquibaseSchema, database.getDatabaseChangeLogTableName()) + ")");
        }

        return StatementLogicFactory.getInstance().generateActions(updateDataStatement, env);

    }
}
