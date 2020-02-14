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

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import com.tcdng.unify.core.UnifyCoreErrorConstants;
import com.tcdng.unify.core.UnifyException;
import com.tcdng.unify.core.annotation.ColumnType;
import com.tcdng.unify.core.criterion.Aggregate;
import com.tcdng.unify.core.criterion.AggregateFunction;
import com.tcdng.unify.core.criterion.Amongst;
import com.tcdng.unify.core.criterion.Select;
import com.tcdng.unify.core.criterion.Update;
import com.tcdng.unify.core.data.Aggregation;
import com.tcdng.unify.core.data.GroupAggregation;
import com.tcdng.unify.core.database.CallableProc;
import com.tcdng.unify.core.database.DatabaseSession;
import com.tcdng.unify.core.database.Entity;
import com.tcdng.unify.core.database.EntityPolicy;
import com.tcdng.unify.core.database.Query;
import com.tcdng.unify.core.util.ReflectUtils;
import com.tcdng.unify.core.util.SqlUtils;

/**
 * Implementation of an SQL database session.
 * 
 * @author Lateef Ojulari
 * @since 1.0
 */
public class SqlDatabaseSessionImpl implements DatabaseSession {

    private SqlDataSource sqlDataSource;
    private SqlDataSourceDialect sqlDataSourceDialect;
    private SqlStatementExecutor sqlStatementExecutor;
    private Connection connection;
    private Stack<Savepoint> savepointStack;
    private boolean closed;

    public SqlDatabaseSessionImpl(SqlDataSource sqlDataSource, SqlStatementExecutor sqlStatementExecutor)
            throws UnifyException {
        this.sqlDataSource = sqlDataSource;
        this.sqlStatementExecutor = sqlStatementExecutor;
        sqlDataSourceDialect = (SqlDataSourceDialect) sqlDataSource.getDialect();
        connection = (Connection) sqlDataSource.getConnection();
        savepointStack = new Stack<Savepoint>();
    }

    @Override
    public String getDataSourceName() {
        return sqlDataSource.getName();
    }

    @Override
    public Object create(Entity record) throws UnifyException {
        SqlEntityInfo sqlEntityInfo = sqlDataSourceDialect.getSqlEntityInfo(SqlUtils.getEntityClass(record));
        if (sqlEntityInfo.isViewOnly()) {
            throw new UnifyException(UnifyCoreErrorConstants.RECORD_VIEW_OPERATION_UNSUPPORTED,
                    sqlEntityInfo.getEntityClass(), "CREATE");
        }

        EntityPolicy entityPolicy = sqlEntityInfo.getEntityPolicy();
        Object id = null;
        if (entityPolicy != null) {
            if (entityPolicy.isSetNow()) {
                id = entityPolicy.preCreate(record, getNow());
            } else {
                id = entityPolicy.preCreate(record, null);
            }
        }
        SqlStatement sqlStatement = sqlDataSourceDialect.prepareCreateStatement(record);
        try {
            getSqlStatementExecutor().executeUpdate(connection, sqlStatement);

            if (sqlEntityInfo.isChildList()) {
                createChildRecords(sqlEntityInfo, record, id);
            }
        } catch (UnifyException e) {
            throw e;
        } finally {
            sqlDataSourceDialect.restoreStatement(sqlStatement);
        }
        return id;
    }

    @Override
    public <T extends Entity> T find(Class<T> clazz, Object id) throws UnifyException {
        return find(clazz, id, true);
    }

    @Override
    public <T extends Entity> T find(Class<T> clazz, Object id, final Object versionNo) throws UnifyException {
        return find(clazz, id, versionNo, true);
    }

    @Override
    public <T extends Entity> T find(Query<T> query) throws UnifyException {
        return find(query, true);
    }

    @Override
    public <T extends Entity> T findLean(Class<T> clazz, Object id) throws UnifyException {
        return find(clazz, id, false);
    }

    @Override
    public <T extends Entity> T findLean(Class<T> clazz, Object id, final Object versionNo) throws UnifyException {
        return find(clazz, id, versionNo, false);
    }

    @Override
    public <T extends Entity> T findLean(Query<T> query) throws UnifyException {
        return find(query, false);
    }

    @SuppressWarnings({ "unchecked" })
    @Override
    public <T extends Entity> T findConstraint(T record) throws UnifyException {
        SqlEntityInfo sqlEntityInfo = sqlDataSourceDialect.getSqlEntityInfo(record.getClass());
        if (sqlEntityInfo.isUniqueConstraints()) {
            Query<T> query = Query.of((Class<T>) record.getClass());
            for (SqlUniqueConstraintInfo suci : sqlEntityInfo.getUniqueConstraintList().values()) {
                query.clear();
                for (String fieldName : suci.getFieldNameList()) {
                    query.addEquals(fieldName, ReflectUtils.getBeanProperty(record, fieldName));
                }
                T constrainingRecord = find(query);
                if (constrainingRecord != null) {
                    return constrainingRecord;
                }
            }

        }
        return null;
    }

    @Override
    public <T extends Entity> List<T> findAll(Query<T> query) throws UnifyException {
        SqlEntityInfo sqlEntityInfo = sqlDataSourceDialect.getSqlEntityInfo(query.getEntityClass());
        EntityPolicy entityPolicy = sqlEntityInfo.getEntityPolicy();
        if (entityPolicy != null) {
            entityPolicy.preQuery(query);
        }

        // Check is fetch from table
        if (sqlEntityInfo.testTrueFieldNamesOnly(query.getRestrictedFields())) {
            return getSqlStatementExecutor().executeMultipleRecordResultQuery(connection,
                    sqlDataSourceDialect.prepareFindStatement(query, false));
        }

        // Fetch from view
        return getSqlStatementExecutor().executeMultipleRecordResultQuery(connection,
                sqlDataSourceDialect.prepareFindStatement(query, true));
    }

    @Override
    public <T, U extends Entity> Map<T, U> findAllMap(Class<T> keyClass, String keyName, Query<U> query)
            throws UnifyException {
        SqlEntityInfo sqlEntityInfo = sqlDataSourceDialect.getSqlEntityInfo(query.getEntityClass());
        EntityPolicy entityPolicy = sqlEntityInfo.getEntityPolicy();
        if (entityPolicy != null) {
            entityPolicy.preQuery(query);
        }

        // Check is fetch from table
        if (sqlEntityInfo.testTrueFieldNamesOnly(query.getRestrictedFields())) {
            return getSqlStatementExecutor().executeMultipleRecordResultQuery(connection, keyClass, keyName,
                    sqlDataSourceDialect.prepareFindStatement(query, false));
        }

        // Fetch from view
        return getSqlStatementExecutor().executeMultipleRecordResultQuery(connection, keyClass, keyName,
                sqlDataSourceDialect.prepareFindStatement(query, true));
    }

    @Override
    public <T, U extends Entity> Map<T, List<U>> findAllListMap(Class<T> keyClass, String keyName, Query<U> query)
            throws UnifyException {
        SqlEntityInfo sqlEntityInfo = sqlDataSourceDialect.getSqlEntityInfo(query.getEntityClass());
        EntityPolicy entityPolicy = sqlEntityInfo.getEntityPolicy();
        if (entityPolicy != null) {
            entityPolicy.preQuery(query);
        }

        // Check is fetch from table
        if (sqlEntityInfo.testTrueFieldNamesOnly(query.getRestrictedFields())) {
            return getSqlStatementExecutor().executeMultipleRecordListResultQuery(connection, keyClass, keyName,
                    sqlDataSourceDialect.prepareFindStatement(query, false));
        }

        // Fetch from view
        return getSqlStatementExecutor().executeMultipleRecordListResultQuery(connection, keyClass, keyName,
                sqlDataSourceDialect.prepareFindStatement(query, true));
    }

    @Override
    public <T extends Entity> T list(Class<T> clazz, Object id) throws UnifyException {
        return list(clazz, id, true);
    }

    @Override
    public <T extends Entity> T list(Class<T> clazz, Object id, final Object versionNo) throws UnifyException {
        return list(clazz, id, versionNo, true);
    }

    @Override
    public <T extends Entity> T list(Query<T> query) throws UnifyException {
        return list(query, true);
    }

    @Override
    public <T extends Entity> T listLean(Class<T> clazz, Object id) throws UnifyException {
        return list(clazz, id, false);
    }

    @Override
    public <T extends Entity> T listLean(Class<T> clazz, Object id, final Object versionNo) throws UnifyException {
        return list(clazz, id, versionNo, false);
    }

    @Override
    public <T extends Entity> T listLean(Query<T> query) throws UnifyException {
        return list(query, false);
    }

    @Override
    public <T extends Entity> List<T> listAll(Query<T> query) throws UnifyException {
        EntityPolicy entityPolicy = sqlDataSourceDialect.getSqlEntityInfo(query.getEntityClass()).getEntityPolicy();
        if (entityPolicy != null) {
            entityPolicy.preQuery(query);
        }

        return getSqlStatementExecutor().executeMultipleRecordResultQuery(connection,
                sqlDataSourceDialect.prepareListStatement(query));
    }

    @Override
    public <T, U extends Entity> Map<T, U> listAll(Class<T> keyClass, String keyName, Query<U> query)
            throws UnifyException {
        EntityPolicy entityPolicy = sqlDataSourceDialect.getSqlEntityInfo(query.getEntityClass()).getEntityPolicy();
        if (entityPolicy != null) {
            entityPolicy.preQuery(query);
        }

        return getSqlStatementExecutor().executeMultipleRecordResultQuery(connection, keyClass, keyName,
                sqlDataSourceDialect.prepareListStatement(query));
    }

    @Override
    public <T, U extends Entity> Map<T, List<U>> listAllListMap(Class<T> keyClass, String keyName, Query<U> query)
            throws UnifyException {
        EntityPolicy entityPolicy = sqlDataSourceDialect.getSqlEntityInfo(query.getEntityClass()).getEntityPolicy();
        if (entityPolicy != null) {
            entityPolicy.preQuery(query);
        }

        return getSqlStatementExecutor().executeMultipleRecordListResultQuery(connection, keyClass, keyName,
                sqlDataSourceDialect.prepareListStatement(query));
    }

    @Override
    public <T, U extends Entity> List<T> valueList(Class<T> fieldClass, String fieldName, final Query<U> query)
            throws UnifyException {
        SqlEntityInfo sqlEntityInfo = sqlDataSourceDialect.getSqlEntityInfo(query.getEntityClass());
        EntityPolicy entityPolicy = sqlEntityInfo.getEntityPolicy();
        if (entityPolicy != null) {
            entityPolicy.preQuery(query);
        }

        Select select = query.getSelect();
        try {
            query.setSelect(new Select(fieldName));
            return getSqlStatementExecutor().executeMultipleObjectListResultQuery(connection, fieldClass,
                    sqlDataSourceDialect.getSqlTypePolicy(sqlEntityInfo.getListFieldInfo(fieldName).getColumnType()),
                    sqlDataSourceDialect.prepareListStatement(query));
        } finally {
            query.setSelect(select);
        }
    }

    @Override
    public <T, U extends Entity> Set<T> valueSet(Class<T> fieldClass, String fieldName, Query<U> query)
            throws UnifyException {
        SqlEntityInfo sqlEntityInfo = sqlDataSourceDialect.getSqlEntityInfo(query.getEntityClass());
        EntityPolicy entityPolicy = sqlEntityInfo.getEntityPolicy();
        if (entityPolicy != null) {
            entityPolicy.preQuery(query);
        }

        Select select = query.getSelect();
        try {
            query.setSelect(new Select(fieldName));
            return getSqlStatementExecutor().executeMultipleObjectSetResultQuery(connection, fieldClass,
                    sqlDataSourceDialect.getSqlTypePolicy(sqlEntityInfo.getListFieldInfo(fieldName).getColumnType()),
                    sqlDataSourceDialect.prepareListStatement(query));
        } finally {
            query.setSelect(select);
        }
    }

    @Override
    public <T, U, V extends Entity> Map<T, U> valueMap(Class<T> keyClass, String keyName, Class<U> valueClass,
            String valueName, Query<V> query) throws UnifyException {
        EntityPolicy entityPolicy = sqlDataSourceDialect.getSqlEntityInfo(query.getEntityClass()).getEntityPolicy();
        if (entityPolicy != null) {
            entityPolicy.preQuery(query);
        }

        Select select = query.getSelect();
        try {
            query.setSelect(new Select(keyName, valueName));
            return getSqlStatementExecutor().executeMultipleObjectMapResultQuery(connection, keyClass, keyName,
                    valueClass, valueName, sqlDataSourceDialect.prepareListStatement(query));
        } finally {
            query.setSelect(select);
        }
    }

    @Override
    public <T, U, V extends Entity> Map<T, List<U>> valueListMap(Class<T> keyClass, String keyName, Class<U> valueClass,
            String valueName, Query<V> query) throws UnifyException {
        EntityPolicy entityPolicy = sqlDataSourceDialect.getSqlEntityInfo(query.getEntityClass()).getEntityPolicy();
        if (entityPolicy != null) {
            entityPolicy.preQuery(query);
        }

        Select select = query.getSelect();
        try {
            query.setSelect(new Select(keyName, valueName));
            return getSqlStatementExecutor().executeMultipleObjectListMapResultQuery(connection, keyClass, keyName,
                    valueClass, valueName, sqlDataSourceDialect.prepareListStatement(query));
        } finally {
            query.setSelect(select);
        }
    }

    @Override
    public <T, U extends Entity> T value(Class<T> fieldClass, String fieldName, Query<U> query) throws UnifyException {
        SqlEntityInfo sqlEntityInfo = sqlDataSourceDialect.getSqlEntityInfo(query.getEntityClass());
        EntityPolicy entityPolicy = sqlEntityInfo.getEntityPolicy();
        if (entityPolicy != null) {
            entityPolicy.preQuery(query);
        }

        Select select = query.getSelect();
        try {
            query.setSelect(new Select(fieldName));
            return getSqlStatementExecutor().executeSingleObjectResultQuery(connection, fieldClass,
                    sqlDataSourceDialect.getSqlTypePolicy(sqlEntityInfo.getListFieldInfo(fieldName).getColumnType()),
                    sqlDataSourceDialect.prepareListStatement(query), query.isMustMatch());
        } finally {
            query.setSelect(select);
        }
    }

    @Override
    public <T, U extends Entity> T min(Class<T> fieldClass, String fieldName, Query<U> query) throws UnifyException {
        SqlEntityInfo sqlEntityInfo = sqlDataSourceDialect.getSqlEntityInfo(query.getEntityClass());
        EntityPolicy entityPolicy = sqlEntityInfo.getEntityPolicy();
        if (entityPolicy != null) {
            entityPolicy.preQuery(query);
        }

        SqlFieldInfo sqlFieldInfo = sqlEntityInfo.getListFieldInfo(fieldName);
        return getSqlStatementExecutor().executeSingleObjectResultQuery(connection, fieldClass,
                sqlDataSourceDialect.getSqlTypePolicy(sqlFieldInfo.getColumnType()),
                sqlDataSourceDialect.prepareMinStatement(sqlFieldInfo.getPreferredColumnName(), query), false);
    }

    @Override
    public <T, U extends Entity> T max(Class<T> fieldClass, String fieldName, Query<U> query) throws UnifyException {
        SqlEntityInfo sqlEntityInfo = sqlDataSourceDialect.getSqlEntityInfo(query.getEntityClass());
        EntityPolicy entityPolicy = sqlEntityInfo.getEntityPolicy();
        if (entityPolicy != null) {
            entityPolicy.preQuery(query);
        }

        SqlFieldInfo sqlFieldInfo = sqlEntityInfo.getListFieldInfo(fieldName);
        return getSqlStatementExecutor().executeSingleObjectResultQuery(connection, fieldClass,
                sqlDataSourceDialect.getSqlTypePolicy(sqlFieldInfo.getColumnType()),
                sqlDataSourceDialect.prepareMaxStatement(sqlFieldInfo.getPreferredColumnName(), query), false);
    }

    @Override
    public void populateListOnly(Entity record) throws UnifyException {
        SqlEntityInfo sqlEntityInfo = sqlDataSourceDialect.getSqlEntityInfo(record.getClass());
        try {
            Map<String, Object> fkItemMap = new HashMap<String, Object>();
            for (SqlForeignKeyInfo sqlForeignKeyInfo : sqlEntityInfo.getForeignKeyList()) {
                SqlFieldInfo fkSqlFieldInfo = sqlForeignKeyInfo.getSqlFieldInfo();
                if (!fkSqlFieldInfo.isIgnoreFkConstraint()) {
                    Object fkId = fkSqlFieldInfo.getGetter().invoke(record);
                    if (fkId != null) {
                        SqlEntityInfo fkSqlEntityInfo = fkSqlFieldInfo.getForeignEntityInfo();
                        Object fkRecord = null;
                        SqlStatement sqlStatement =
                                sqlDataSourceDialect.prepareListByPkStatement(fkSqlEntityInfo.getKeyClass(), fkId);
                        try {
                            fkRecord = getSqlStatementExecutor().executeSingleRecordResultQuery(connection,
                                    sqlStatement, true);
                            if (fkRecord == null) {
                                throw new UnifyException(UnifyCoreErrorConstants.RECORD_WITH_PK_NOT_FOUND,
                                        fkSqlEntityInfo.getKeyClass(), fkId);
                            }
                        } finally {
                            sqlDataSourceDialect.restoreStatement(sqlStatement);
                        }

                        fkItemMap.put(fkSqlFieldInfo.getName(), fkRecord);
                    }
                }
            }

            if (!fkItemMap.isEmpty()) {
                for (SqlFieldInfo sqlFieldInfo : sqlEntityInfo.getListFieldInfos()) {
                    if (sqlFieldInfo.isListOnly()) {
                        Object fkRecord = fkItemMap.get(sqlFieldInfo.getForeignKeyFieldInfo().getName());
                        if (fkRecord != null) {
                            Object val = sqlFieldInfo.getForeignFieldInfo().getGetter().invoke(fkRecord);
                            sqlFieldInfo.getSetter().invoke(record, val);
                        }
                    }
                }
            }
        } catch (UnifyException e) {
            throw e;
        } catch (Exception e) {
            throw new UnifyException(UnifyCoreErrorConstants.COMPONENT_OPERATION_ERROR, e);
        }
    }

    @Override
    public int updateById(Entity record) throws UnifyException {
        return updateById(record, true);
    }

    @Override
    public int updateByIdVersion(Entity record) throws UnifyException {
        return updateByIdVersion(record, true);
    }

    @Override
    public int updateLeanById(Entity record) throws UnifyException {
        return updateById(record, false);
    }

    @Override
    public int updateLeanByIdVersion(Entity record) throws UnifyException {
        return updateByIdVersion(record, false);
    }

    @Override
    public int updateById(Class<? extends Entity> clazz, Object id, Update update) throws UnifyException {
        return getSqlStatementExecutor().executeUpdate(connection,
                sqlDataSourceDialect.prepareUpdateStatement(clazz, id, update));
    }

    @Override
    public int updateAll(Query<? extends Entity> query, Update update) throws UnifyException {
        try {
            SqlEntityInfo sqlEntityInfo = sqlDataSourceDialect.getSqlEntityInfo(SqlUtils.getEntityClass(query));
            EntityPolicy entityPolicy = sqlEntityInfo.getEntityPolicy();
            if (entityPolicy != null) {
                entityPolicy.preQuery(query);
            }

            if (sqlEntityInfo.isViewOnly()) {
                throw new UnifyException(UnifyCoreErrorConstants.RECORD_VIEW_OPERATION_UNSUPPORTED,
                        sqlEntityInfo.getEntityClass(), "UPDATE");
            }

            if (sqlEntityInfo.isViewOnly()) {
                throw new UnifyException(UnifyCoreErrorConstants.RECORD_VIEW_OPERATION_UNSUPPORTED,
                        sqlEntityInfo.getEntityClass(), "UPDATE");
            }

            if (sqlDataSourceDialect.isQueryOffsetOrLimit(query)
                    || (sqlEntityInfo.testTrueFieldNamesOnly(query.getRestrictedFields()))) {
                return getSqlStatementExecutor().executeUpdate(connection,
                        sqlDataSourceDialect.prepareUpdateStatement(query, update));
            }

            SqlFieldInfo idFieldInfo = sqlEntityInfo.getIdFieldInfo();
            List<?> idList = valueList(idFieldInfo.getFieldType(), idFieldInfo.getName(), query);
            if (!idList.isEmpty()) {
                Query<? extends Entity> updateQuery = query.copyNoAll();
                updateQuery.addRestriction(new Amongst(idFieldInfo.getName(), idList));
                return getSqlStatementExecutor().executeUpdate(connection,
                        sqlDataSourceDialect.prepareUpdateStatement(updateQuery, update));
            }
        } catch (UnifyException e) {
            throw e;
        } catch (Exception e) {
            throw new UnifyException(e, UnifyCoreErrorConstants.COMPONENT_OPERATION_ERROR, getClass().getSimpleName());
        }
        return 0;
    }

    @Override
    public int deleteById(Entity record) throws UnifyException {
        SqlEntityInfo sqlEntityInfo = sqlDataSourceDialect.getSqlEntityInfo(record.getClass());
        if (sqlEntityInfo.isViewOnly()) {
            throw new UnifyException(UnifyCoreErrorConstants.RECORD_VIEW_OPERATION_UNSUPPORTED,
                    sqlEntityInfo.getEntityClass(), "DELETE");
        }

        SqlStatement sqlStatement = sqlDataSourceDialect.prepareDeleteByPkStatement(record.getClass(), record.getId());
        EntityPolicy entityPolicy = sqlEntityInfo.getEntityPolicy();
        int result;
        try {
            if (entityPolicy != null) {
                if (entityPolicy.isSetNow()) {
                    entityPolicy.preDelete(record, getNow());
                } else {
                    entityPolicy.preDelete(record, null);
                }
            }

            if (sqlEntityInfo.isOnDeleteCascadeList()) {
                deleteChildRecords(sqlEntityInfo, record.getId());
            }

            result = getSqlStatementExecutor().executeUpdate(connection, sqlStatement);
            if (result == 0) {
                throw new UnifyException(UnifyCoreErrorConstants.RECORD_WITH_PK_NOT_FOUND, record.getClass(),
                        record.getId());
            }
        } catch (Exception e) {
            if (entityPolicy != null) {
                entityPolicy.onDeleteError(record);
            }

            if (e instanceof UnifyException) {
                throw ((UnifyException) e);
            }

            throw new UnifyException(e, UnifyCoreErrorConstants.DATASOURCE_SESSION_ERROR, getDataSourceName());
        } finally {
            sqlDataSourceDialect.restoreStatement(sqlStatement);
        }
        return result;
    }

    @Override
    public int deleteByIdVersion(Entity record) throws UnifyException {
        SqlEntityInfo sqlEntityInfo = sqlDataSourceDialect.getSqlEntityInfo(record.getClass());
        if (sqlEntityInfo.isViewOnly()) {
            throw new UnifyException(UnifyCoreErrorConstants.RECORD_VIEW_OPERATION_UNSUPPORTED,
                    sqlEntityInfo.getEntityClass(), "DELETE");
        }

        EntityPolicy entityPolicy = sqlEntityInfo.getEntityPolicy();
        SqlStatement sqlStatement = null;
        int result;
        try {
            Object oldVersionNo = null;
            if (sqlEntityInfo.isVersioned()) {
                oldVersionNo = sqlEntityInfo.getVersionFieldInfo().getGetter().invoke(record);
                if (entityPolicy != null) {
                    if (entityPolicy.isSetNow()) {
                        entityPolicy.preDelete(record, getNow());
                    } else {
                        entityPolicy.preDelete(record, null);
                    }
                }

                sqlStatement = sqlDataSourceDialect.prepareDeleteByPkVersionStatement(record.getClass(), record.getId(),
                        oldVersionNo);
            } else {
                if (entityPolicy != null) {
                    if (entityPolicy.isSetNow()) {
                        entityPolicy.preDelete(record, getNow());
                    } else {
                        entityPolicy.preDelete(record, null);
                    }
                }

                sqlStatement = sqlDataSourceDialect.prepareDeleteByPkStatement(record.getClass(), record.getId());
            }

            if (sqlEntityInfo.isOnDeleteCascadeList()) {
                deleteChildRecords(sqlEntityInfo, record.getId());
            }

            result = getSqlStatementExecutor().executeUpdate(connection, sqlStatement);
            if (result == 0) {
                throw new UnifyException(UnifyCoreErrorConstants.RECORD_WITH_PK_VERSION_NOT_FOUND, record.getClass(),
                        record.getId(), oldVersionNo);
            }
        } catch (Exception e) {
            if (entityPolicy != null) {
                entityPolicy.onDeleteError(record);
            }

            if (e instanceof UnifyException) {
                throw ((UnifyException) e);
            }

            throw new UnifyException(e, UnifyCoreErrorConstants.DATASOURCE_SESSION_ERROR, getDataSourceName());
        } finally {
            sqlDataSourceDialect.restoreStatement(sqlStatement);
        }
        return result;
    }

    @Override
    public int delete(Class<? extends Entity> clazz, final Object id) throws UnifyException {
        SqlEntityInfo sqlEntityInfo = sqlDataSourceDialect.getSqlEntityInfo(clazz);
        if (sqlEntityInfo.isViewOnly()) {
            throw new UnifyException(UnifyCoreErrorConstants.RECORD_VIEW_OPERATION_UNSUPPORTED,
                    sqlEntityInfo.getEntityClass(), "DELETE");
        }

        SqlStatement sqlStatement = sqlDataSourceDialect.prepareDeleteByPkStatement(clazz, id);
        int result;
        try {
            if (sqlEntityInfo.isOnDeleteCascadeList()) {
                deleteChildRecords(sqlEntityInfo, id);
            }

            result = getSqlStatementExecutor().executeUpdate(connection, sqlStatement);
            if (result == 0) {
                throw new UnifyException(UnifyCoreErrorConstants.RECORD_WITH_PK_NOT_FOUND, clazz, id);
            }
        } catch (UnifyException e) {
            throw e;
        } catch (Exception e) {
            throw new UnifyException(e, UnifyCoreErrorConstants.DATASOURCE_SESSION_ERROR, getDataSourceName());
        } finally {
            sqlDataSourceDialect.restoreStatement(sqlStatement);
        }
        return result;
    }

    @Override
    public int deleteAll(Query<? extends Entity> query) throws UnifyException {
        try {
            SqlEntityInfo sqlEntityInfo = sqlDataSourceDialect.getSqlEntityInfo(query.getEntityClass());
            EntityPolicy entityPolicy = sqlEntityInfo.getEntityPolicy();
            if (entityPolicy != null) {
                entityPolicy.preQuery(query);
            }

            if (sqlEntityInfo.isViewOnly()) {
                throw new UnifyException(UnifyCoreErrorConstants.RECORD_VIEW_OPERATION_UNSUPPORTED,
                        sqlEntityInfo.getEntityClass(), "DELETE");
            }

            if (sqlDataSourceDialect.isQueryOffsetOrLimit(query) || (!sqlEntityInfo.isChildList()
                    && sqlEntityInfo.testTrueFieldNamesOnly(query.getRestrictedFields()))) {
                return getSqlStatementExecutor().executeUpdate(connection,
                        sqlDataSourceDialect.prepareDeleteStatement(query));
            }

            SqlFieldInfo idFieldInfo = sqlEntityInfo.getIdFieldInfo();
            List<?> idList = valueList(idFieldInfo.getFieldType(), idFieldInfo.getName(), query);
            if (!idList.isEmpty()) {
                if (sqlEntityInfo.isOnDeleteCascadeList()) {
                    for (OnDeleteCascadeInfo odci : sqlEntityInfo.getOnDeleteCascadeInfoList()) {
                        Query<? extends Entity> attrQuery = Query.of(odci.getChildEntityClass());
                        attrQuery.addAmongst(odci.getChildFkField().getName(), idList);
                        deleteAll(attrQuery);
                    }
                }

                Query<? extends Entity> deleteQuery = query.copyNoAll();
                deleteQuery.addRestriction(new Amongst(idFieldInfo.getName(), idList));
                return getSqlStatementExecutor().executeUpdate(connection,
                        sqlDataSourceDialect.prepareDeleteStatement(deleteQuery));
            }
        } catch (UnifyException e) {
            throw e;
        } catch (Exception e) {
            throw new UnifyException(e, UnifyCoreErrorConstants.COMPONENT_OPERATION_ERROR, getClass().getSimpleName());
        }
        return 0;
    }

    @Override
    public int count(Query<? extends Entity> query) throws UnifyException {
        SqlEntityInfo sqlEntityInfo = sqlDataSourceDialect.getSqlEntityInfo(query.getEntityClass());
        EntityPolicy entityPolicy = sqlEntityInfo.getEntityPolicy();
        if (entityPolicy != null) {
            entityPolicy.preQuery(query);
        }

        // Check is fetch from table
        if (sqlEntityInfo.testTrueFieldNamesOnly(query.getRestrictedFields())) {
            return getSqlStatementExecutor().executeSingleObjectResultQuery(connection, int.class,
                    sqlDataSourceDialect.getSqlTypePolicy(int.class),
                    sqlDataSourceDialect.prepareCountStatement(query, false), true);
        }

        // Fetch from view
        return getSqlStatementExecutor().executeSingleObjectResultQuery(connection, int.class,
                sqlDataSourceDialect.getSqlTypePolicy(int.class),
                sqlDataSourceDialect.prepareCountStatement(query, true), true);
    }

    @Override
    public Date getNow() throws UnifyException {
        return getSqlStatementExecutor().executeSingleObjectResultQuery(connection, Date.class,
                sqlDataSourceDialect.getSqlTypePolicy(ColumnType.TIMESTAMP_UTC),
                sqlDataSourceDialect.generateUTCTimestampSql(), true);
    }

    @Override
    public Aggregation aggregate(AggregateFunction aggregateFunction, Query<? extends Entity> query)
            throws UnifyException {
        try {
            SqlEntityInfo sqlEntityInfo = sqlDataSourceDialect.getSqlEntityInfo(query.getEntityClass());
            EntityPolicy entityPolicy = sqlEntityInfo.getEntityPolicy();
            if (entityPolicy != null) {
                entityPolicy.preQuery(query);
            }

            if (sqlEntityInfo.testTrueFieldNamesOnly(query.getRestrictedFields())) {
                return getSqlStatementExecutor().executeSingleAggregateResultQuery(aggregateFunction, connection,
                        sqlDataSourceDialect.getSqlTypePolicy(int.class),
                        sqlDataSourceDialect.prepareAggregateStatement(aggregateFunction, query));
            }

            SqlFieldInfo idFieldInfo = sqlEntityInfo.getIdFieldInfo();
            List<?> idList = valueList(idFieldInfo.getFieldType(), idFieldInfo.getName(), query);
            if (!idList.isEmpty()) {
                Query<? extends Entity> aggregateQuery = query.copyNoCriteria();
                aggregateQuery.addRestriction(new Amongst(idFieldInfo.getName(), idList));
                return getSqlStatementExecutor().executeSingleAggregateResultQuery(aggregateFunction, connection,
                        sqlDataSourceDialect.getSqlTypePolicy(int.class),
                        sqlDataSourceDialect.prepareAggregateStatement(aggregateFunction, aggregateQuery));
            }
        } catch (UnifyException e) {
            throw e;
        } catch (Exception e) {
            throw new UnifyException(e, UnifyCoreErrorConstants.COMPONENT_OPERATION_ERROR, getClass().getSimpleName());
        }
        return null;
    }

    @Override
    public List<Aggregation> aggregateMany(Aggregate aggregate, Query<? extends Entity> query) throws UnifyException {
        try {
            SqlEntityInfo sqlEntityInfo = sqlDataSourceDialect.getSqlEntityInfo(query.getEntityClass());
            EntityPolicy entityPolicy = sqlEntityInfo.getEntityPolicy();
            if (entityPolicy != null) {
                entityPolicy.preQuery(query);
            }

            if (sqlEntityInfo.testTrueFieldNamesOnly(query.getRestrictedFields())) {
                return getSqlStatementExecutor().executeMultipleAggregateResultQuery(aggregate.getFunctionList(),
                        connection, sqlDataSourceDialect.getSqlTypePolicy(int.class),
                        sqlDataSourceDialect.prepareAggregateStatement(aggregate.getFunctionList(), query));
            }

            SqlFieldInfo idFieldInfo = sqlEntityInfo.getIdFieldInfo();
            List<?> idList = valueList(idFieldInfo.getFieldType(), idFieldInfo.getName(), query);
            if (!idList.isEmpty()) {
                Query<? extends Entity> aggregateQuery = query.copyNoCriteria();
                aggregateQuery.addRestriction(new Amongst(idFieldInfo.getName(), idList));
                return getSqlStatementExecutor().executeMultipleAggregateResultQuery(aggregate.getFunctionList(),
                        connection, sqlDataSourceDialect.getSqlTypePolicy(int.class),
                        sqlDataSourceDialect.prepareAggregateStatement(aggregate.getFunctionList(), aggregateQuery));
            }
        } catch (UnifyException e) {
            throw e;
        } catch (Exception e) {
            throw new UnifyException(e, UnifyCoreErrorConstants.COMPONENT_OPERATION_ERROR, getClass().getSimpleName());
        }
        return Collections.emptyList();
    }

    @Override
    public List<GroupAggregation> aggregateGroupMany(Aggregate aggregate, Query<? extends Entity> query)
            throws UnifyException {
        try {
            SqlEntityInfo sqlEntityInfo = sqlDataSourceDialect.getSqlEntityInfo(query.getEntityClass());
            EntityPolicy entityPolicy = sqlEntityInfo.getEntityPolicy();
            if (entityPolicy != null) {
                entityPolicy.preQuery(query);
            }

            if (sqlEntityInfo.testTrueFieldNamesOnly(query.getRestrictedFields())) {
                return getSqlStatementExecutor().executeMultipleAggregateResultQuery(aggregate.getFunctionList(),
                        query.getGroupBy(), connection, sqlDataSourceDialect.getSqlTypePolicy(int.class),
                        sqlDataSourceDialect.prepareAggregateStatement(aggregate.getFunctionList(), query));
            }

            SqlFieldInfo idFieldInfo = sqlEntityInfo.getIdFieldInfo();
            List<?> idList = valueList(idFieldInfo.getFieldType(), idFieldInfo.getName(), query);
            if (!idList.isEmpty()) {
                Query<? extends Entity> aggregateQuery = query.copyNoCriteria();
                aggregateQuery.addRestriction(new Amongst(idFieldInfo.getName(), idList));
                return getSqlStatementExecutor().executeMultipleAggregateResultQuery(aggregate.getFunctionList(),
                        query.getGroupBy(), connection, sqlDataSourceDialect.getSqlTypePolicy(int.class),
                        sqlDataSourceDialect.prepareAggregateStatement(aggregate.getFunctionList(), aggregateQuery));
            }
        } catch (UnifyException e) {
            throw e;
        } catch (Exception e) {
            throw new UnifyException(e, UnifyCoreErrorConstants.COMPONENT_OPERATION_ERROR, getClass().getSimpleName());
        }
        return Collections.emptyList();
    }

    @Override
    public void executeCallable(CallableProc callableProc) throws UnifyException {
        SqlCallableStatement sqlCallableStatement = sqlDataSourceDialect.prepareCallableStatement(callableProc);
        try {
            getSqlStatementExecutor().executeCallable(connection, callableProc, sqlCallableStatement);
        } finally {
            sqlDataSourceDialect.restoreCallableStatement(sqlCallableStatement);
        }
    }

    @Override
    public Map<Class<?>, List<?>> executeCallableWithResults(CallableProc callableProc) throws UnifyException {
        SqlCallableStatement sqlCallableStatement = sqlDataSourceDialect.prepareCallableStatement(callableProc);
        try {
            return getSqlStatementExecutor().executeCallableWithResults(connection, callableProc, sqlCallableStatement);
        } finally {
            sqlDataSourceDialect.restoreCallableStatement(sqlCallableStatement);
        }
    }

    @Override
    public void clearSavepoint() throws UnifyException {
        try {
            if (!savepointStack.isEmpty()) {
                Savepoint savepoint = savepointStack.pop();
                connection.releaseSavepoint(savepoint);
            }
        } catch (SQLException e) {
            throw new UnifyException(e, UnifyCoreErrorConstants.DATASOURCE_SESSION_ERROR, getDataSourceName());
        }

    }

    @Override
    public void close() throws UnifyException {
        try {
            while (!savepointStack.empty()) {
                connection.releaseSavepoint(savepointStack.pop());
            }
        } catch (SQLException e) {
        }
        sqlDataSource.restoreConnection(connection);
        connection = null;
        closed = true;
    }

    @Override
    public void commit() throws UnifyException {
        try {
            connection.commit();
        } catch (SQLException e) {
            throw new UnifyException(e, UnifyCoreErrorConstants.DATASOURCE_SESSION_ERROR, getDataSourceName());
        }
    }

    @Override
    public void rollback() throws UnifyException {
        try {
            connection.rollback();
        } catch (SQLException e) {
            throw new UnifyException(e, UnifyCoreErrorConstants.DATASOURCE_SESSION_ERROR, getDataSourceName());
        }
    }

    @Override
    public void rollbackToSavepoint() throws UnifyException {
        try {
            if (!savepointStack.isEmpty()) {
                Savepoint savepoint = savepointStack.peek();
                connection.rollback(savepoint);
            }
        } catch (SQLException e) {
            throw new UnifyException(e, UnifyCoreErrorConstants.DATASOURCE_SESSION_ERROR, getDataSourceName());
        }
    }

    @Override
    public void setSavepoint() throws UnifyException {
        try {
            Savepoint savepoint = connection.setSavepoint();
            savepointStack.push(savepoint);
        } catch (SQLException e) {
            throw new UnifyException(e, UnifyCoreErrorConstants.DATASOURCE_SESSION_ERROR, getDataSourceName());
        }
    }

    @Override
    public boolean isClosed() throws UnifyException {
        return closed;
    }

    private <T extends Entity> T find(Class<T> clazz, Object id, boolean fetchChild) throws UnifyException {
        SqlStatement sqlStatement = sqlDataSourceDialect.prepareFindByPkStatement(clazz, id);
        try {
            T record = getSqlStatementExecutor().executeSingleRecordResultQuery(connection, sqlStatement, true);
            if (record == null) {
                throw new UnifyException(UnifyCoreErrorConstants.RECORD_WITH_PK_NOT_FOUND, clazz, id);
            }

            if (fetchChild) {
                fetchChildRecords(record, null, false);
            }
            return record;
        } finally {
            sqlDataSourceDialect.restoreStatement(sqlStatement);
        }
    }

    private <T extends Entity> T find(Class<T> clazz, Object id, final Object versionNo, boolean fetchChild)
            throws UnifyException {
        SqlStatement sqlStatement = sqlDataSourceDialect.prepareFindByPkVersionStatement(clazz, id, versionNo);
        try {
            T record = getSqlStatementExecutor().executeSingleRecordResultQuery(connection, sqlStatement, true);
            if (record == null) {
                throw new UnifyException(UnifyCoreErrorConstants.RECORD_WITH_PK_VERSION_NOT_FOUND, clazz, id,
                        versionNo);
            }

            if (fetchChild) {
                fetchChildRecords(record, null, false);
            }
            return record;
        } finally {
            sqlDataSourceDialect.restoreStatement(sqlStatement);
        }
    }

    private <T extends Entity> T find(Query<T> query, boolean fetchChild) throws UnifyException {
        T record = null;
        try {
            SqlEntityInfo sqlEntityInfo = sqlDataSourceDialect.getSqlEntityInfo(SqlUtils.getEntityClass(query));
            EntityPolicy entityPolicy = sqlEntityInfo.getEntityPolicy();
            if (entityPolicy != null) {
                entityPolicy.preQuery(query);
            }

            // Check is fetch from table
            if (sqlEntityInfo.testTrueFieldNamesOnly(query.getRestrictedFields())) {
                record = getSqlStatementExecutor().executeSingleRecordResultQuery(connection,
                        sqlDataSourceDialect.prepareFindStatement(query, false), false);
            } else {
                // Fetch from view
                record = getSqlStatementExecutor().executeSingleRecordResultQuery(connection,
                        sqlDataSourceDialect.prepareFindStatement(query, true), false);
            }

            if (fetchChild) {
                fetchChildRecords(record, query.getSelect(), false);
            }
        } catch (UnifyException e) {
            throw e;
        } catch (Exception e) {
            throw new UnifyException(e, UnifyCoreErrorConstants.COMPONENT_OPERATION_ERROR, getClass().getSimpleName());
        }
        return record;
    }

    private <T extends Entity> T list(Class<T> clazz, Object id, boolean fetchChild) throws UnifyException {
        SqlStatement sqlStatement = sqlDataSourceDialect.prepareListByPkStatement(clazz, id);
        try {
            T record = getSqlStatementExecutor().executeSingleRecordResultQuery(connection, sqlStatement, true);
            if (record == null) {
                throw new UnifyException(UnifyCoreErrorConstants.RECORD_WITH_PK_NOT_FOUND, clazz, id);
            }

            if (fetchChild) {
                fetchChildRecords(record, null, true);
            }
            return record;
        } finally {
            sqlDataSourceDialect.restoreStatement(sqlStatement);
        }
    }

    private <T extends Entity> T list(Class<T> clazz, Object id, final Object versionNo, boolean fetchChild)
            throws UnifyException {
        SqlStatement sqlStatement = sqlDataSourceDialect.prepareListByPkVersionStatement(clazz, id, versionNo);
        try {
            T record = getSqlStatementExecutor().executeSingleRecordResultQuery(connection, sqlStatement, true);
            if (record == null) {
                throw new UnifyException(UnifyCoreErrorConstants.RECORD_WITH_PK_VERSION_NOT_FOUND, clazz, id,
                        versionNo);
            }

            if (fetchChild) {
                fetchChildRecords(record, null, true);
            }
            return record;
        } finally {
            sqlDataSourceDialect.restoreStatement(sqlStatement);
        }
    }

    private <T extends Entity> T list(Query<T> query, boolean fetchChild) throws UnifyException {
        EntityPolicy entityPolicy =
                sqlDataSourceDialect.getSqlEntityInfo(SqlUtils.getEntityClass(query)).getEntityPolicy();
        if (entityPolicy != null) {
            entityPolicy.preQuery(query);
        }

        T record = getSqlStatementExecutor().executeSingleRecordResultQuery(connection,
                sqlDataSourceDialect.prepareListStatement(query), false);
        if (fetchChild) {
            fetchChildRecords(record, query.getSelect(), true);
        }
        return record;
    }

    private <T extends Entity> void fetchChildRecords(T record, Select select, boolean isListOnly)
            throws UnifyException {
        if (record != null) {
            try {
                SqlEntityInfo sqlEntityInfo = sqlDataSourceDialect.getSqlEntityInfo(SqlUtils.getEntityClass(record));
                if (sqlEntityInfo.isChildList()) {
                    boolean isSelect = select != null && !select.isEmpty();
                    Object id = record.getId();

                    if (sqlEntityInfo.isSingleChildList()) {
                        for (ChildFieldInfo clfi : sqlEntityInfo.getSingleChildInfoList()) {
                            if (isSelect && !select.contains(clfi.getName())) {
                                continue;
                            }

                            SqlEntityInfo childSqlEntityInfo =
                                    sqlDataSourceDialect.getSqlEntityInfo(clfi.getChildEntityClass());
                            Query<? extends Entity> query = Query.of(clfi.getChildEntityClass());
                            query.addEquals(clfi.getChildFkField().getName(), id)
                                    .addOrder(childSqlEntityInfo.getIdFieldInfo().getName());
                            List<? extends Entity> childList = null;
                            if (isListOnly) {
                                childList = listAll(query);
                            } else {
                                childList = findAll(query);
                            }

                            // Check if child has child list and load if necessary
                            Entity childRecord = null;
                            if (!childList.isEmpty()) {
                                if (childList.size() > 1) {
                                    throw new UnifyException(UnifyCoreErrorConstants.RECORD_MULTIPLE_CHILD_FOUND,
                                            record.getClass(), record.getId(), clfi.getField().getName());
                                }

                                childRecord = childList.get(0);
                                if (childSqlEntityInfo.isChildList()) {
                                    fetchChildRecords(childRecord, null, isListOnly);
                                }
                            }

                            // Set child record
                            clfi.getSetter().invoke(record, childRecord);
                        }
                    }

                    if (sqlEntityInfo.isManyChildList()) {
                        for (ChildFieldInfo clfi : sqlEntityInfo.getManyChildInfoList()) {
                            if (isSelect && !select.contains(clfi.getName())) {
                                continue;
                            }

                            SqlEntityInfo childSqlEntityInfo =
                                    sqlDataSourceDialect.getSqlEntityInfo(clfi.getChildEntityClass());
                            Query<? extends Entity> query = Query.of(clfi.getChildEntityClass());
                            query.addEquals(clfi.getChildFkField().getName(), id)
                                    .addOrder(childSqlEntityInfo.getIdFieldInfo().getName());
                            List<? extends Entity> childList = null;
                            if (isListOnly) {
                                childList = listAll(query);
                            } else {
                                childList = findAll(query);
                            }

                            // Check if child has child list and load if necessary
                            if (!childList.isEmpty()) {
                                if (childSqlEntityInfo.isChildList()) {
                                    for (Entity childRecord : childList) {
                                        fetchChildRecords(childRecord, null, isListOnly);
                                    }
                                }
                            }

                            // Set child list
                            clfi.getSetter().invoke(record, childList);
                        }
                    }
                }
            } catch (UnifyException e) {
                throw e;
            } catch (Exception e) {
                throw new UnifyException(e, UnifyCoreErrorConstants.COMPONENT_OPERATION_ERROR,
                        getClass().getSimpleName());
            }
        }
    }

    private int updateById(Entity record, boolean updateChild) throws UnifyException {
        int result;
        SqlStatement sqlStatement = null;
        SqlEntityInfo sqlEntityInfo = sqlDataSourceDialect.getSqlEntityInfo(SqlUtils.getEntityClass(record));
        if (sqlEntityInfo.isViewOnly()) {
            throw new UnifyException(UnifyCoreErrorConstants.RECORD_VIEW_OPERATION_UNSUPPORTED,
                    sqlEntityInfo.getEntityClass(), "UPDATE");
        }

        EntityPolicy entityPolicy = sqlEntityInfo.getEntityPolicy();
        try {
            if (entityPolicy != null) {
                if (entityPolicy.isSetNow()) {
                    entityPolicy.preUpdate(record, getNow());
                } else {
                    entityPolicy.preUpdate(record, null);
                }
            }

            sqlStatement = sqlDataSourceDialect.prepareUpdateByPkStatement(record);
            result = getSqlStatementExecutor().executeUpdate(connection, sqlStatement);
            if (result == 0) {
                throw new UnifyException(UnifyCoreErrorConstants.RECORD_WITH_PK_NOT_FOUND, record.getClass(),
                        record.getId());
            }

            if (updateChild && sqlEntityInfo.isChildList()) {
                updateChildRecords(sqlEntityInfo, record);
            }
        } catch (UnifyException e) {
            if (entityPolicy != null) {
                entityPolicy.onUpdateError(record);
            }
            throw e;
        } finally {
            if (sqlStatement != null) {
                sqlDataSourceDialect.restoreStatement(sqlStatement);
            }
        }
        return result;
    }

    private int updateByIdVersion(Entity record, boolean updateChild) throws UnifyException {
        int result;
        SqlStatement sqlStatement = null;
        SqlEntityInfo sqlEntityInfo = sqlDataSourceDialect.getSqlEntityInfo(SqlUtils.getEntityClass(record));
        if (sqlEntityInfo.isViewOnly()) {
            throw new UnifyException(UnifyCoreErrorConstants.RECORD_VIEW_OPERATION_UNSUPPORTED,
                    sqlEntityInfo.getEntityClass(), "UPDATE");
        }

        EntityPolicy entityPolicy = sqlEntityInfo.getEntityPolicy();
        try {
            Object oldVersionNo = null;
            if (sqlEntityInfo.isVersioned()) {
                oldVersionNo = sqlEntityInfo.getVersionFieldInfo().getGetter().invoke(record);
                if (entityPolicy != null) {
                    if (entityPolicy.isSetNow()) {
                        entityPolicy.preUpdate(record, getNow());
                    } else {
                        entityPolicy.preUpdate(record, null);
                    }
                }
                sqlStatement = sqlDataSourceDialect.prepareUpdateByPkVersionStatement(record, oldVersionNo);
            } else {
                if (entityPolicy != null) {
                    if (entityPolicy.isSetNow()) {
                        entityPolicy.preUpdate(record, getNow());
                    } else {
                        entityPolicy.preUpdate(record, null);
                    }
                }
                sqlStatement = sqlDataSourceDialect.prepareUpdateByPkStatement(record);
            }

            result = getSqlStatementExecutor().executeUpdate(connection, sqlStatement);
            if (result == 0) {
                throw new UnifyException(UnifyCoreErrorConstants.RECORD_WITH_PK_VERSION_NOT_FOUND, record.getClass(),
                        sqlEntityInfo.getIdFieldInfo().getGetter().invoke(record), oldVersionNo);
            }

            if (updateChild && sqlEntityInfo.isChildList()) {
                updateChildRecords(sqlEntityInfo, record);
            }
        } catch (Exception e) {
            if (entityPolicy != null) {
                entityPolicy.onUpdateError(record);
            }

            if (e instanceof UnifyException) {
                throw ((UnifyException) e);
            }
            throw new UnifyException(e, UnifyCoreErrorConstants.COMPONENT_OPERATION_ERROR, getClass().getSimpleName());
        } finally {
            if (sqlStatement != null) {
                sqlDataSourceDialect.restoreStatement(sqlStatement);
            }
        }
        return result;
    }

    private void updateChildRecords(SqlEntityInfo sqlEntityInfo, Entity record) throws UnifyException {
        Object id = record.getId();
        deleteChildRecords(sqlEntityInfo, id);
        createChildRecords(sqlEntityInfo, record, id);
    }

    @SuppressWarnings({ "unchecked" })
    private void createChildRecords(SqlEntityInfo sqlEntityInfo, Entity record, Object id) throws UnifyException {
        try {
            if (sqlEntityInfo.isSingleChildList()) {
                for (ChildFieldInfo alfi : sqlEntityInfo.getSingleChildInfoList()) {
                    Entity childRecord = (Entity) alfi.getGetter().invoke(record);
                    if (childRecord != null) {
                        alfi.getAttrFkSetter().invoke(childRecord, id);
                        create(childRecord);
                    }
                }
            }

            if (sqlEntityInfo.isManyChildList()) {
                for (ChildFieldInfo alfi : sqlEntityInfo.getManyChildInfoList()) {
                    List<? extends Entity> attrList = (List<? extends Entity>) alfi.getGetter().invoke(record);
                    if (attrList != null) {
                        Method attrFkSetter = alfi.getAttrFkSetter();
                        for (Entity attrRecord : attrList) {
                            attrFkSetter.invoke(attrRecord, id);
                            create(attrRecord);
                        }
                    }
                }
            }
        } catch (UnifyException e) {
            throw e;
        } catch (Exception e) {
            throw new UnifyException(e, UnifyCoreErrorConstants.COMPONENT_OPERATION_ERROR, getClass().getSimpleName());
        }
    }

    private void deleteChildRecords(SqlEntityInfo sqlEntityInfo, Object id) throws UnifyException {
        for (OnDeleteCascadeInfo odci : sqlEntityInfo.getOnDeleteCascadeInfoList()) {
            Query<? extends Entity> query = Query.of(odci.getChildEntityClass());
            query.addEquals(odci.getChildFkField().getName(), id);
            deleteAll(query);
        }
    }

    private SqlStatementExecutor getSqlStatementExecutor() throws UnifyException {
        if (closed) {
            throw new UnifyException(UnifyCoreErrorConstants.DATASOURCE_SESSION_IS_CLOSED, getDataSourceName());
        }
        return sqlStatementExecutor;
    }
}
