/*
  * Copyright 2018-2020 The Code Department.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.tcdng.unify.core.database.sql.dialect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.tcdng.unify.core.UnifyException;
import com.tcdng.unify.core.annotation.ColumnType;
import com.tcdng.unify.core.annotation.Component;
import com.tcdng.unify.core.database.sql.AbstractSqlDataSourceDialect;
import com.tcdng.unify.core.database.sql.SqlColumnAlterInfo;
import com.tcdng.unify.core.database.sql.SqlColumnInfo;
import com.tcdng.unify.core.database.sql.SqlDataTypePolicy;
import com.tcdng.unify.core.database.sql.SqlDialectNameConstants;
import com.tcdng.unify.core.database.sql.SqlEntitySchemaInfo;
import com.tcdng.unify.core.database.sql.SqlFieldSchemaInfo;
import com.tcdng.unify.core.database.sql.SqlUniqueConstraintSchemaInfo;
import com.tcdng.unify.core.database.sql.data.policy.BlobPolicy;
import com.tcdng.unify.core.util.StringUtils;

/**
 * MySQL SQL dialect.
 * 
 * @author Lateef Ojulari
 * @since 1.0
 */
@Component(name = SqlDialectNameConstants.MYSQL, description = "$m{sqldialect.mysqldb}")
public class MySqlDialect extends AbstractSqlDataSourceDialect {

    public MySqlDialect() {
        super(true);
    }

    @Override
    public String generateTestSql() throws UnifyException {
        return "SELECT 1";
    }

    @Override
    public String generateUTCTimestampSql() throws UnifyException {
        return "SELECT UTC_TIMESTAMP";
    }

    @Override
    public int getMaxClauseValues() {
        return -1;
    }

    @Override
    public String generateDropUniqueConstraintSql(SqlEntitySchemaInfo sqlRecordSchemaInfo,
            SqlUniqueConstraintSchemaInfo sqlUniqueConstraintInfo, boolean format) throws UnifyException {
        StringBuilder sb = new StringBuilder();
        String tableName = sqlRecordSchemaInfo.getSchemaTableName();
        sb.append("ALTER TABLE ").append(tableName);
        if (format) {
            sb.append(getLineSeparator());
        } else {
            sb.append(" ");
        }
        sb.append("DROP INDEX ").append(tableName).append("_").append(sqlUniqueConstraintInfo.getName().toUpperCase())
                .append("UK");
        return sb.toString();
    }

    @Override
    public String generateRenameColumn(SqlEntitySchemaInfo sqlRecordSchemaInfo, SqlFieldSchemaInfo sqlFieldSchemaInfo,
            SqlFieldSchemaInfo oldSqlFieldSchemaInfo, boolean format) throws UnifyException {
        StringBuilder sb = new StringBuilder();
        sb.append("ALTER TABLE ").append(sqlRecordSchemaInfo.getSchemaTableName());
        if (format) {
            sb.append(getLineSeparator());
        } else {
            sb.append(' ');
        }
        sb.append("CHANGE COLUMN ").append(oldSqlFieldSchemaInfo.getPreferredColumnName()).append(' ');
        appendColumnAndTypeSql(sb, sqlFieldSchemaInfo, true);
        return sb.toString();
    }

    @Override
    public List<String> generateAlterColumn(SqlEntitySchemaInfo sqlEntitySchemaInfo,
            SqlFieldSchemaInfo sqlFieldSchemaInfo, SqlColumnAlterInfo sqlColumnAlterInfo, boolean format)
            throws UnifyException {
        if (sqlColumnAlterInfo.isAltered()) {
            List<String> sqlList = new ArrayList<String>();
            StringBuilder sb = new StringBuilder();
            SqlDataTypePolicy sqlDataTypePolicy = getSqlTypePolicy(sqlFieldSchemaInfo.getColumnType());

            if (sqlColumnAlterInfo.isNullableChange()) {
                if (!sqlFieldSchemaInfo.isNullable()) {
                    sb.append("UPDATE ").append(sqlEntitySchemaInfo.getSchemaTableName()).append(" SET ")
                            .append(sqlFieldSchemaInfo.getPreferredColumnName()).append(" = ");
                    sqlDataTypePolicy.appendDefaultVal(sb, sqlFieldSchemaInfo.getFieldType(),
                            sqlFieldSchemaInfo.getDefaultVal());
                    sb.append(" WHERE ").append(sqlFieldSchemaInfo.getPreferredColumnName()).append(" IS NULL");
                    sqlList.add(sb.toString());
                    StringUtils.truncate(sb);
                }
            }

            sb.append("ALTER TABLE ").append(sqlEntitySchemaInfo.getSchemaTableName());
            if (format) {
                sb.append(getLineSeparator());
            } else {
                sb.append(' ');
            }
            sb.append("MODIFY ");
            appendColumnAndTypeSql(sb, sqlFieldSchemaInfo, true);
            sqlList.add(sb.toString());
            StringUtils.truncate(sb);
            return sqlList;
        }

        return Collections.emptyList();
    }

    @Override
    public String generateAlterColumnNull(SqlEntitySchemaInfo sqlEntitySchemaInfo, SqlColumnInfo sqlColumnInfo,
            boolean format) throws UnifyException {
        StringBuilder sb = new StringBuilder();
        sb.append("ALTER TABLE ").append(sqlEntitySchemaInfo.getSchemaTableName());
        if (format) {
            sb.append(getLineSeparator());
        } else {
            sb.append(' ');
        }
        sb.append("MODIFY ").append(sqlColumnInfo.getColumnName());
        appendTypeSql(sb, sqlColumnInfo);
        sb.append(" NULL");
        return sb.toString();
    }

    @Override
    protected void onInitialize() throws UnifyException {
        super.onInitialize();

        setDataTypePolicy(ColumnType.BLOB, new MySqlBlobPolicy());
    }

    @Override
    protected boolean appendLimitOffsetInfixClause(StringBuilder sql, int offset, int limit) throws UnifyException {
        return false;
    }

    @Override
    protected boolean appendWhereLimitOffsetSuffixClause(StringBuilder sql, int offset, int limit, boolean append)
            throws UnifyException {
        return false;
    }

    @Override
    protected boolean appendLimitOffsetSuffixClause(StringBuilder sql, int offset, int limit, boolean append)
            throws UnifyException {
        boolean isAppend = false;
        if (limit > 0) {
            sql.append(" LIMIT ").append(limit);
            isAppend = true;
        }

        if (offset > 0) {
            sql.append(" OFFSET ").append(offset);
            isAppend = true;
        }

        return isAppend;
    }
}

class MySqlBlobPolicy extends BlobPolicy {

    @Override
    public void appendTypeSql(StringBuilder sb, int length, int precision, int scale) {
        sb.append(" MEDIUMBLOB");
    }
}