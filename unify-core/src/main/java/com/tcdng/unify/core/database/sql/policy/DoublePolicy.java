/*
 * Copyright 2018-2019 The Code Department.
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
package com.tcdng.unify.core.database.sql.policy;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;

import com.tcdng.unify.core.database.sql.SqlDataTypePolicy;
import com.tcdng.unify.core.util.StringUtils;

/**
 * Double data type SQL policy.
 * 
 * @author Lateef Ojulari
 * @since 1.0
 */
public class DoublePolicy implements SqlDataTypePolicy {

    @Override
    public void appendTypeSql(StringBuilder sb, int length, int precision, int scale) {
        sb.append("FLOAT");
    }

    @Override
    public void appendSpecifyDefaultValueSql(StringBuilder sb, Class<?> type, String defaultVal) {
        if (!StringUtils.isBlank(defaultVal)) {
            sb.append(" DEFAULT ").append(Double.valueOf(defaultVal));
        }
    }

    @Override
    public void executeSetPreparedStatement(Object pstmt, int index, Object data) throws Exception {
        if (data == null) {
            ((PreparedStatement) pstmt).setNull(index, Types.DOUBLE);
        } else {
            ((PreparedStatement) pstmt).setDouble(index, (Double) data);
        }
    }

    @Override
    public Object executeGetResult(Object rs, Class<?> type, String column) throws Exception {
        Object object = ((ResultSet) rs).getObject(column);
        if (object != null) {
            return ((ResultSet) rs).getDouble(column);
        }
        return null;
    }

    @Override
    public Object executeGetResult(Object rs, Class<?> type, int index) throws Exception {
        Object object = ((ResultSet) rs).getObject(index);
        if (object != null) {
            return ((ResultSet) rs).getDouble(index);
        }
        return null;
    }

    @Override
    public int getSqlType() {
        return Types.DOUBLE;
    }

    @Override
    public boolean isFixedLength() {
        return true;
    }

}
