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
package com.tcdng.unify.core.database.sql;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.tcdng.unify.core.AbstractUnifyComponent;
import com.tcdng.unify.core.ApplicationComponents;
import com.tcdng.unify.core.UnifyCoreErrorConstants;
import com.tcdng.unify.core.UnifyException;
import com.tcdng.unify.core.annotation.Component;
import com.tcdng.unify.core.constant.EnumConst;
import com.tcdng.unify.core.data.Aggregate;
import com.tcdng.unify.core.database.CallableProc;
import com.tcdng.unify.core.database.Entity;
import com.tcdng.unify.core.database.StaticReference;
import com.tcdng.unify.core.transform.Transformer;
import com.tcdng.unify.core.util.DataUtils;
import com.tcdng.unify.core.util.SqlUtils;

/**
 * Default implementation of an SQL statement executor.
 * 
 * @author Lateef Ojulari
 * @since 1.0
 */
@Component(ApplicationComponents.APPLICATION_SQLSTATEMENTEXECUTOR)
public class SqlStatementExecutorImpl extends AbstractUnifyComponent implements SqlStatementExecutor {

    @Override
    public int executeUpdate(Connection connection, SqlStatement sqlStatement) throws UnifyException {
        int result = 0;
        PreparedStatement pStmt = null;
        try {
            long timeZoneOffset = getSessionContext().getTimeZoneOffset();
            pStmt = getPreparedStatement(connection, sqlStatement, timeZoneOffset);
            result = pStmt.executeUpdate();
        } catch (UnifyException e) {
            throw e;
        } catch (Exception e) {
            throwOperationErrorException(e);
        } finally {
            SqlUtils.close(pStmt);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T executeSingleObjectResultQuery(Connection connection, Class<T> clazz,
            SqlDataTypePolicy sqlDataTypePolicy, SqlStatement sqlStatement, boolean mustMatch) throws UnifyException {
        T result = null;
        PreparedStatement pStmt = null;
        ResultSet rs = null;

        try {
            long timeZoneOffset = getSessionContext().getTimeZoneOffset();
            pStmt = getPreparedStatement(connection, sqlStatement, timeZoneOffset);
            rs = pStmt.executeQuery();
            if (rs.next()) {
                result = (T) sqlDataTypePolicy.executeGetResult(rs, clazz, 1, timeZoneOffset);
                if (rs.next()) {
                    throw new UnifyException(UnifyCoreErrorConstants.RECORD_MULTIPLE_RESULT_FOUND);
                }
            } else if (mustMatch) {
                throw new UnifyException(UnifyCoreErrorConstants.RECORD_SINGLEOBJECT_NO_MATCHING_RECORD, clazz);
            }
        } catch (UnifyException e) {
            throw e;
        } catch (Exception e) {
            throwOperationErrorException(e);
        } finally {
            SqlUtils.close(rs);
            SqlUtils.close(pStmt);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T executeSingleObjectResultQuery(Connection connection, Class<T> clazz,
            SqlDataTypePolicy sqlDataTypePolicy, String sqlQuery, boolean mustMatch) throws UnifyException {
        T result = null;
        PreparedStatement pStmt = null;
        ResultSet rs = null;

        try {
            pStmt = connection.prepareStatement(sqlQuery);
            rs = pStmt.executeQuery();
            if (rs.next()) {
                long timeZoneOffset = getSessionContext().getTimeZoneOffset();
                result = (T) sqlDataTypePolicy.executeGetResult(rs, clazz, 1, timeZoneOffset);
                if (rs.next()) {
                    throw new UnifyException(UnifyCoreErrorConstants.RECORD_MULTIPLE_RESULT_FOUND);
                }
            } else if (mustMatch) {
                throw new UnifyException(UnifyCoreErrorConstants.RECORD_SINGLEOBJECT_NO_MATCHING_RECORD, clazz);
            }
        } catch (UnifyException e) {
            throw e;
        } catch (Exception e) {
            throwOperationErrorException(e);
        } finally {
            SqlUtils.close(rs);
            SqlUtils.close(pStmt);
        }
        return result;
    }

    @Override
    public <T> List<T> executeMultipleObjectListResultQuery(Connection connection, Class<T> clazz,
            SqlDataTypePolicy sqlDataTypePolicy, SqlStatement sqlStatement) throws UnifyException {
        return executeMultipleObjectResultQuery(new ArrayList<T>(), connection, clazz, sqlDataTypePolicy, sqlStatement);
    }

    @Override
    public <T> Set<T> executeMultipleObjectSetResultQuery(Connection connection, Class<T> clazz,
            SqlDataTypePolicy sqlDataTypePolicy, SqlStatement sqlStatement) throws UnifyException {
        return executeMultipleObjectResultQuery(new HashSet<T>(), connection, clazz, sqlDataTypePolicy, sqlStatement);
    }

    @SuppressWarnings("resource")
    @Override
    public <T, U> Map<T, U> executeMultipleObjectMapResultQuery(Connection connection, Class<T> keyClass, String key,
            Class<U> valueClass, String value, SqlStatement sqlStatement) throws UnifyException {
        SqlResult keySqlResult = null;
        SqlResult valueSqlResult = null;
        for (SqlResult sqlResult : sqlStatement.getResultInfoList()) {
            if (sqlResult.getName().equals(key)) {
                keySqlResult = sqlResult;
                if (valueSqlResult != null) {
                    break;
                }
            }

            if (sqlResult.getName().equals(value)) {
                valueSqlResult = sqlResult;
                if (keySqlResult != null) {
                    break;
                }
            }
        }

        SqlEntityInfo sqlEntityInfo = sqlStatement.getSqlEntityInfo();
        if (keySqlResult == null) {
            throw new UnifyException(UnifyCoreErrorConstants.RECORD_FIELDINFO_NOT_FOUND, sqlEntityInfo.getKeyClass(),
                    key);
        }

        if (valueSqlResult == null) {
            throw new UnifyException(UnifyCoreErrorConstants.RECORD_FIELDINFO_NOT_FOUND, sqlEntityInfo.getKeyClass(),
                    value);
        }

        Map<T, U> resultMap = new HashMap<T, U>();
        PreparedStatement pStmt = null;
        ResultSet rs = null;

        try {
            long timeZoneOffset = getSessionContext().getTimeZoneOffset();
            pStmt = getPreparedStatement(connection, sqlStatement, timeZoneOffset);
            rs = pStmt.executeQuery();
            while (rs.next()) {
                T keyValue = getSqlResultValue(keySqlResult, rs, timeZoneOffset);
                if (resultMap.containsKey(keyValue)) {
                    throw new UnifyException(UnifyCoreErrorConstants.VALUE_MULTIPLE_SAME_KEY_FOUND, keyValue,
                            sqlEntityInfo.getEntityClass());
                }

                U valueValue = getSqlResultValue(valueSqlResult, rs, timeZoneOffset);
                resultMap.put(keyValue, valueValue);
            }
        } catch (UnifyException e) {
            throw e;
        } catch (Exception e) {
            throwOperationErrorException(e);
        } finally {
            SqlUtils.close(rs);
            SqlUtils.close(pStmt);
        }
        return resultMap;
    }

    @Override
    public <T, U> Map<T, List<U>> executeMultipleObjectListMapResultQuery(Connection connection, Class<T> keyClass,
            String key, Class<U> valueClass, String value, SqlStatement sqlStatement) throws UnifyException {
        SqlResult keySqlResult = null;
        SqlResult valueSqlResult = null;
        for (SqlResult sqlResult : sqlStatement.getResultInfoList()) {
            if (sqlResult.getName().equals(key)) {
                keySqlResult = sqlResult;
                if (valueSqlResult != null) {
                    break;
                }
            }

            if (sqlResult.getName().equals(value)) {
                valueSqlResult = sqlResult;
                if (keySqlResult != null) {
                    break;
                }
            }
        }

        SqlEntityInfo sqlEntityInfo = sqlStatement.getSqlEntityInfo();
        if (keySqlResult == null) {
            throw new UnifyException(UnifyCoreErrorConstants.RECORD_FIELDINFO_NOT_FOUND, sqlEntityInfo.getKeyClass(),
                    key);
        }

        if (valueSqlResult == null) {
            throw new UnifyException(UnifyCoreErrorConstants.RECORD_FIELDINFO_NOT_FOUND, sqlEntityInfo.getKeyClass(),
                    value);
        }

        Map<T, List<U>> resultMap = new HashMap<T, List<U>>();
        PreparedStatement pStmt = null;
        ResultSet rs = null;

        try {
            long timeZoneOffset = getSessionContext().getTimeZoneOffset();
            pStmt = getPreparedStatement(connection, sqlStatement, timeZoneOffset);
            rs = pStmt.executeQuery();
            while (rs.next()) {
                T keyValue = getSqlResultValue(keySqlResult, rs, timeZoneOffset);
                U valueValue = getSqlResultValue(valueSqlResult, rs, timeZoneOffset);

                List<U> list = resultMap.get(keyValue);
                if (list == null) {
                    list = new ArrayList<U>();
                    resultMap.put(keyValue, list);
                }
                list.add(valueValue);
            }
        } catch (UnifyException e) {
            throw e;
        } catch (Exception e) {
            throwOperationErrorException(e);
        } finally {
            SqlUtils.close(rs);
            SqlUtils.close(pStmt);
        }
        return resultMap;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends Entity> T executeSingleRecordResultQuery(Connection connection, SqlStatement sqlStatement,
            boolean mustMatch) throws UnifyException {
        T result = null;
        PreparedStatement pStmt = null;
        ResultSet rs = null;
        try {
            SqlEntityInfo sqlEntityInfo = sqlStatement.getSqlEntityInfo();
            long timeZoneOffset = getSessionContext().getTimeZoneOffset();
            pStmt = getPreparedStatement(connection, sqlStatement, timeZoneOffset);
            rs = pStmt.executeQuery();
            if (rs.next()) {
                if (sqlEntityInfo.isEnumConst()) {
                    result = (T) new StaticReference(sqlEntityInfo.getEnumConstClass());
                } else {
                    result = (T) sqlEntityInfo.getEntityClass().newInstance();
                }

                for (SqlResult sqlResult : sqlStatement.getResultInfoList()) {
                    sqlResult.getSetter().invoke(result, getSqlResultValue(sqlResult, rs, timeZoneOffset));
                }

                if (rs.next()) {
                    throw new UnifyException(UnifyCoreErrorConstants.RECORD_MULTIPLE_RESULT_FOUND);
                }
            } else if (mustMatch) {
                throw new UnifyException(UnifyCoreErrorConstants.RECORD_NO_MATCHING_RECORD,
                        sqlEntityInfo.getEntityClass());
            }
        } catch (UnifyException e) {
            throw e;
        } catch (Exception e) {
            throwOperationErrorException(e);
        } finally {
            SqlUtils.close(rs);
            SqlUtils.close(pStmt);
        }

        return result;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends Entity> List<T> executeMultipleRecordResultQuery(Connection connection, SqlStatement sqlStatement)
            throws UnifyException {
        List<T> resultList = new ArrayList<T>();
        PreparedStatement pStmt = null;
        ResultSet rs = null;

        try {
            SqlEntityInfo sqlEntityInfo = sqlStatement.getSqlEntityInfo();
            Class<? extends Entity> entityClass = sqlEntityInfo.getEntityClass();
            Class<? extends EnumConst> enumConstClass = sqlEntityInfo.getEnumConstClass();
            boolean isEnumConst = sqlEntityInfo.isEnumConst();
            long timeZoneOffset = getSessionContext().getTimeZoneOffset();
            pStmt = getPreparedStatement(connection, sqlStatement, timeZoneOffset);
            rs = pStmt.executeQuery();
            while (rs.next()) {
                T record = null;
                if (isEnumConst) {
                    record = (T) new StaticReference(enumConstClass);
                } else {
                    record = (T) entityClass.newInstance();
                }

                for (SqlResult sqlResult : sqlStatement.getResultInfoList()) {
                    sqlResult.getSetter().invoke(record, getSqlResultValue(sqlResult, rs, timeZoneOffset));
                }
                resultList.add(record);
            }
        } catch (UnifyException e) {
            throw e;
        } catch (Exception e) {
            throwOperationErrorException(e);
        } finally {
            SqlUtils.close(rs);
            SqlUtils.close(pStmt);
        }

        return resultList;
    }

    @SuppressWarnings({ "unchecked", "resource" })
    @Override
    public <T, U extends Entity> Map<T, U> executeMultipleRecordResultQuery(Connection connection, Class<T> keyClass,
            String key, SqlStatement sqlStatement) throws UnifyException {
        if (key == null) {
            throw new UnifyException(UnifyCoreErrorConstants.RECORD_CRITERIA_KEY_REQUIRED);
        }

        SqlResult keySQLFieldInfo = null;
        for (SqlResult sqlResult : sqlStatement.getResultInfoList()) {
            if (sqlResult.getName().equals(key)) {
                keySQLFieldInfo = sqlResult;
                break;
            }
        }

        SqlEntityInfo sqlEntityInfo = sqlStatement.getSqlEntityInfo();
        if (keySQLFieldInfo == null) {
            throw new UnifyException(UnifyCoreErrorConstants.RECORD_FIELDINFO_NOT_FOUND, sqlEntityInfo.getKeyClass(),
                    key);
        }

        Map<T, U> resultMap = new LinkedHashMap<T, U>();
        PreparedStatement pStmt = null;
        ResultSet rs = null;

        try {
            Class<? extends Entity> entityClass = sqlEntityInfo.getEntityClass();
            Class<? extends EnumConst> enumConstClass = sqlEntityInfo.getEnumConstClass();
            boolean isEnumConst = sqlEntityInfo.isEnumConst();
            long timeZoneOffset = getSessionContext().getTimeZoneOffset();
            pStmt = getPreparedStatement(connection, sqlStatement, timeZoneOffset);
            rs = pStmt.executeQuery();
            while (rs.next()) {
                U record = null;
                if (isEnumConst) {
                    record = (U) new StaticReference(enumConstClass);
                } else {
                    record = (U) entityClass.newInstance();
                }

                for (SqlResult sqlResult : sqlStatement.getResultInfoList()) {
                    sqlResult.getSetter().invoke(record, getSqlResultValue(sqlResult, rs, timeZoneOffset));
                }

                T keyVal = (T) keySQLFieldInfo.getGetter().invoke(record);
                if (resultMap.containsKey(keyVal)) {
                    throw new UnifyException(UnifyCoreErrorConstants.RECORD_MULTIPLE_SAME_KEY_FOUND, keyVal,
                            sqlEntityInfo.getEntityClass());
                }

                resultMap.put(keyVal, record);
            }
        } catch (UnifyException e) {
            throw e;
        } catch (Exception e) {
            throwOperationErrorException(e);
        } finally {
            SqlUtils.close(rs);
            SqlUtils.close(pStmt);
        }

        return resultMap;
    }

    @SuppressWarnings({ "unchecked" })
    @Override
    public <T, U extends Entity> Map<T, List<U>> executeMultipleRecordListResultQuery(Connection connection,
            Class<T> keyClass, String key, SqlStatement sqlStatement) throws UnifyException {
        if (key == null) {
            throw new UnifyException(UnifyCoreErrorConstants.RECORD_CRITERIA_KEY_REQUIRED);
        }

        SqlResult keySQLFieldInfo = null;
        for (SqlResult sqlResult : sqlStatement.getResultInfoList()) {
            if (sqlResult.getName().equals(key)) {
                keySQLFieldInfo = sqlResult;
                break;
            }
        }

        SqlEntityInfo sqlEntityInfo = sqlStatement.getSqlEntityInfo();
        if (keySQLFieldInfo == null) {
            throw new UnifyException(UnifyCoreErrorConstants.RECORD_FIELDINFO_NOT_FOUND, sqlEntityInfo.getKeyClass(),
                    key);
        }

        Map<T, List<U>> resultMap = new LinkedHashMap<T, List<U>>();
        PreparedStatement pStmt = null;
        ResultSet rs = null;

        try {
            Class<? extends Entity> entityClass = sqlEntityInfo.getEntityClass();
            Class<? extends EnumConst> enumConstClass = sqlEntityInfo.getEnumConstClass();
            boolean isEnumConst = sqlEntityInfo.isEnumConst();
            long timeZoneOffset = getSessionContext().getTimeZoneOffset();
            pStmt = getPreparedStatement(connection, sqlStatement, timeZoneOffset);
            rs = pStmt.executeQuery();
            while (rs.next()) {
                U record = null;
                if (isEnumConst) {
                    record = (U) new StaticReference(enumConstClass);
                } else {
                    record = (U) entityClass.newInstance();
                }

                for (SqlResult sqlResult : sqlStatement.getResultInfoList()) {
                    sqlResult.getSetter().invoke(record, getSqlResultValue(sqlResult, rs, timeZoneOffset));
                }

                T keyVal = (T) keySQLFieldInfo.getGetter().invoke(record);
                List<U> list = resultMap.get(keyVal);
                if (list == null) {
                    list = new ArrayList<U>();
                    resultMap.put(keyVal, list);
                }
                list.add(record);
            }
        } catch (UnifyException e) {
            throw e;
        } catch (Exception e) {
            throwOperationErrorException(e);
        } finally {
            SqlUtils.close(rs);
            SqlUtils.close(pStmt);
        }

        return resultMap;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Override
    public Aggregate<?> executeSingleAggregateResultQuery(Connection connection,
            SqlDataTypePolicy countSqlDataTypePolicy, SqlStatement sqlStatement) throws UnifyException {
        PreparedStatement pStmt = null;
        ResultSet rs = null;
        try {
            long timeZoneOffset = getSessionContext().getTimeZoneOffset();
            pStmt = getPreparedStatement(connection, sqlStatement, timeZoneOffset);
            rs = pStmt.executeQuery();
            if (rs.next()) {
                int resultIndex = 0;
                int count =
                        ((Number) countSqlDataTypePolicy.executeGetResult(rs, int.class, ++resultIndex, timeZoneOffset))
                                .intValue();
                List<SqlResult> sqlResultList = sqlStatement.getResultInfoList();
                if (sqlResultList.isEmpty() || sqlResultList.size() > 1) {
                    throw new UnifyException(UnifyCoreErrorConstants.RECORD_MULTIPLE_OR_NOFIELD_SELECTED);
                }
                
                SqlResult sqlResult = sqlResultList.get(0);
                Object value = sqlResult.getSqlDataTypePolicy().executeGetResult(rs, sqlResult.getType(),
                        ++resultIndex, timeZoneOffset);
                if (value == null) {
                    value = DataUtils.convert(sqlResult.getType(), 0, null);
                }

                if (rs.next()) {
                    throw new UnifyException(UnifyCoreErrorConstants.RECORD_MULTIPLE_RESULT_FOUND);
                }

                return new Aggregate(sqlResult.getName(), count, value);
            }
        } catch (UnifyException e) {
            throw e;
        } catch (Exception e) {
            throwOperationErrorException(e);
        } finally {
            SqlUtils.close(rs);
            SqlUtils.close(pStmt);
        }
        return null;
    }
    
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Override
    public List<Aggregate<?>> executeMultipleAggregateResultQuery(Connection connection,
            SqlDataTypePolicy countSqlDataTypePolicy, SqlStatement sqlStatement) throws UnifyException {
        List<Aggregate<?>> resultList = new ArrayList<Aggregate<?>>();
        PreparedStatement pStmt = null;
        ResultSet rs = null;
        try {
            long timeZoneOffset = getSessionContext().getTimeZoneOffset();
            pStmt = getPreparedStatement(connection, sqlStatement, timeZoneOffset);
            rs = pStmt.executeQuery();
            if (rs.next()) {
                int resultIndex = 0;
                int count =
                        ((Number) countSqlDataTypePolicy.executeGetResult(rs, int.class, ++resultIndex, timeZoneOffset))
                                .intValue();
                for (SqlResult sqlResult : sqlStatement.getResultInfoList()) {
                    Object value = sqlResult.getSqlDataTypePolicy().executeGetResult(rs, sqlResult.getType(),
                            ++resultIndex, timeZoneOffset);
                    if (value == null) {
                        value = DataUtils.convert(sqlResult.getType(), 0, null);
                    }
                    resultList.add(new Aggregate(sqlResult.getName(), count, value));
                }

                if (rs.next()) {
                    throw new UnifyException(UnifyCoreErrorConstants.RECORD_MULTIPLE_RESULT_FOUND);
                }
            }
        } catch (UnifyException e) {
            throw e;
        } catch (Exception e) {
            throwOperationErrorException(e);
        } finally {
            SqlUtils.close(rs);
            SqlUtils.close(pStmt);
        }
        return resultList;
    }

    @Override
    public void executeCallable(Connection connection, CallableProc callableProc,
            SqlCallableStatement sqlCallableStatement) throws UnifyException {
        CallableStatement cStmt = null;
        ResultSet rs = null;
        try {
            // Prepare and execute callable statement
            long timeZoneOffset = getSessionContext().getTimeZoneOffset();
            cStmt = getCallableStatement(connection, sqlCallableStatement, timeZoneOffset);

            if (sqlCallableStatement.isWithReturn() && sqlCallableStatement.isFunctionMode()) {
                cStmt.execute();
                rs = cStmt.getResultSet();
                if (rs.next()) {
                    SqlCallableInfo sqlCallableInfo = sqlCallableStatement.getSqlCallableInfo();
                    SqlCallableFieldInfo returnValueInfo = sqlCallableInfo.getReturnValueInfo();
                    Object val = sqlCallableStatement.getReturnTypePolicy().executeGetResult(rs,
                            returnValueInfo.getGetter().getReturnType(), 1, timeZoneOffset);
                    returnValueInfo.getSetter().invoke(callableProc, val);
                }
            } else {
                cStmt.executeUpdate();
            }

            // Populate output parameters. Based on recommendations do this after results.
            populateCallableOutParameters(cStmt, callableProc, sqlCallableStatement, timeZoneOffset);
        } catch (UnifyException e) {
            throw e;
        } catch (Exception e) {
            throwOperationErrorException(e);
        } finally {
            SqlUtils.close(rs);
            SqlUtils.close(cStmt);
        }
    }

    @Override
    public Map<Class<?>, List<?>> executeCallableWithResults(Connection connection, CallableProc callableProc,
            SqlCallableStatement sqlCallableStatement) throws UnifyException {
        CallableStatement cStmt = null;
        ResultSet rs = null;
        try {
            // Prepare and execute callable statement
            long timeZoneOffset = getSessionContext().getTimeZoneOffset();
            cStmt = getCallableStatement(connection, sqlCallableStatement, timeZoneOffset);

            boolean isResults = cStmt.execute();
            if (!isResults) {
                isResults = cStmt.getMoreResults();
            }

            // Construct and populate results
            Map<Class<?>, List<?>> results = Collections.emptyMap();
            Map<Class<?>, SqlCallableResult> resultInfoMap = sqlCallableStatement.getResultInfoListByType();
            if (isResults && resultInfoMap.size() > 0) {
                results = new LinkedHashMap<Class<?>, List<?>>();
                for (Class<?> resultClass : resultInfoMap.keySet()) {
                    SqlCallableResult sqlCallableResult = resultInfoMap.get(resultClass);
                    List<Object> resultItemList = new ArrayList<Object>();
                    rs = cStmt.getResultSet();
                    while (rs.next()) {
                        Object item = resultClass.newInstance();
                        if (sqlCallableResult.isUseIndexing()) {
                            int index = 0;
                            for (SqlCallableResultField resultField : sqlCallableResult.getFieldList()) {
                                resultField.getSetter().invoke(item, resultField.getSqlDataTypePolicy()
                                        .executeGetResult(rs, resultField.getType(), ++index, timeZoneOffset));
                            }
                        } else {
                            for (SqlCallableResultField resultField : sqlCallableResult.getFieldList()) {
                                resultField.getSetter().invoke(item,
                                        resultField.getSqlDataTypePolicy().executeGetResult(rs, resultField.getType(),
                                                resultField.getColumnName(), timeZoneOffset));
                            }
                        }

                        resultItemList.add(item);
                    }

                    results.put(resultClass, resultItemList);
                    cStmt.getMoreResults();
                }
            }

            // Populate output parameters. Based on recommendations do this after results.
            populateCallableOutParameters(cStmt, callableProc, sqlCallableStatement, timeZoneOffset);

            return results;
        } catch (UnifyException e) {
            throw e;
        } catch (Exception e) {
            throwOperationErrorException(e);
        } finally {
            SqlUtils.close(rs);
            SqlUtils.close(cStmt);
        }

        return Collections.emptyMap();
    }

    @Override
    protected void onInitialize() throws UnifyException {

    }

    @Override
    protected void onTerminate() throws UnifyException {

    }

    @SuppressWarnings("unchecked")
    private PreparedStatement getPreparedStatement(Connection connection, SqlStatement sqlStatement,
            long timeZoneOffset) throws Exception {
        logDebug("Preparing SQl: statement = {0}", sqlStatement);
        PreparedStatement pStmt = connection.prepareStatement(sqlStatement.getSql());
        int index = 0;
        for (SqlParameter sqlParameter : sqlStatement.getParameterInfoList()) {
            Object value = sqlParameter.getValue();
            if (sqlParameter.isMultiple()) {
                for (Object arrValue : (Collection<Object>) value) {
                    sqlParameter.getSqlTypePolicy().executeSetPreparedStatement(pStmt, ++index, arrValue,
                            timeZoneOffset);
                }
            } else {
                sqlParameter.getSqlTypePolicy().executeSetPreparedStatement(pStmt, ++index, value, timeZoneOffset);
            }
        }
        return pStmt;
    }

    private CallableStatement getCallableStatement(Connection connection, SqlCallableStatement sqlCallableStatement,
            long timeZoneOffset) throws Exception {
        logDebug("Preparing SQl: callable statement = {0}", sqlCallableStatement);
        CallableStatement cStmt = connection.prepareCall(sqlCallableStatement.getSql());
        int sqlIndex = 1;
        if (sqlCallableStatement.isWithReturn() && !sqlCallableStatement.isFunctionMode()) {
            sqlCallableStatement.getReturnTypePolicy().executeRegisterOutParameter(cStmt, sqlIndex);
            sqlIndex++;
        }

        for (SqlParameter sqlParameter : sqlCallableStatement.getParameterInfoList()) {
            if (sqlParameter.isInput()) {
                Object value = sqlParameter.getValue();
                sqlParameter.getSqlTypePolicy().executeSetPreparedStatement(cStmt, sqlIndex, value, timeZoneOffset);
            }

            if (sqlParameter.isOutput()) {
                sqlParameter.getSqlTypePolicy().executeRegisterOutParameter(cStmt, sqlIndex);
            }

            sqlIndex++;
        }
        return cStmt;
    }

    @SuppressWarnings("unchecked")
    private <T> T getSqlResultValue(SqlResult sqlResult, ResultSet rs, long timeZoneOffset) throws Exception {
        Object value = sqlResult.getSqlDataTypePolicy().executeGetResult(rs, sqlResult.getType(),
                sqlResult.getColumnName(), timeZoneOffset);
        if (sqlResult.isTransformed()) {
            value = ((Transformer<Object, Object>) sqlResult.getTransformer()).reverseTransform(value);
        }
        return (T) value;
    }

    @SuppressWarnings("unchecked")
    private <T, U extends Collection<T>> U executeMultipleObjectResultQuery(Collection<T> result, Connection connection,
            Class<T> clazz, SqlDataTypePolicy sqlDataTypePolicy, SqlStatement sqlStatement) throws UnifyException {
        PreparedStatement pStmt = null;
        ResultSet rs = null;
        try {
            long timeZoneOffset = getSessionContext().getTimeZoneOffset();
            pStmt = getPreparedStatement(connection, sqlStatement, timeZoneOffset);

            rs = pStmt.executeQuery();

            Transformer<Object, Object> transformer =
                    (Transformer<Object, Object>) sqlStatement.getResultInfoList().get(0).getTransformer();
            if (transformer == null) {
                while (rs.next()) {
                    result.add((T) sqlDataTypePolicy.executeGetResult(rs, clazz, 1, timeZoneOffset));
                }
            } else {
                while (rs.next()) {
                    result.add((T) transformer
                            .reverseTransform(sqlDataTypePolicy.executeGetResult(rs, clazz, 1, timeZoneOffset)));
                }
            }
        } catch (UnifyException e) {
            throw e;
        } catch (Exception e) {
            throwOperationErrorException(e);
        } finally {
            SqlUtils.close(rs);
            SqlUtils.close(pStmt);
        }
        return (U) result;
    }

    private void populateCallableOutParameters(CallableStatement cStmt, CallableProc callableProc,
            SqlCallableStatement sqlCallableStatement, long timeZoneOffset) throws Exception {
        while (cStmt.getMoreResults()) {
            // Nothing here. Just clearing results if any left.
        }

        // Return value if any
        SqlCallableInfo sqlCallableInfo = sqlCallableStatement.getSqlCallableInfo();
        int sqlIndex = 1;
        if (sqlCallableStatement.isWithReturn() && !sqlCallableStatement.isFunctionMode()) {
            SqlCallableFieldInfo returnValueInfo = sqlCallableInfo.getReturnValueInfo();
            Object val = sqlCallableStatement.getReturnTypePolicy().executeGetOutput(cStmt,
                    returnValueInfo.getGetter().getReturnType(), sqlIndex++, timeZoneOffset);
            returnValueInfo.getSetter().invoke(callableProc, val);
        }

        // Populate out parameters
        int index = 0;
        for (SqlParameter sqlParameter : sqlCallableStatement.getParameterInfoList()) {
            if (sqlParameter.isOutput()) {
                SqlCallableParamInfo sqlCallableParamInfo = sqlCallableInfo.getParamInfoList().get(index);
                Object val = sqlParameter.getSqlTypePolicy().executeGetOutput(cStmt,
                        sqlCallableParamInfo.getField().getType(), sqlIndex, timeZoneOffset);
                sqlCallableParamInfo.getSetter().invoke(callableProc, val);
            }
            sqlIndex++;
            index++;
        }
    }
}
