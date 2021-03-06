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

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

import com.tcdng.unify.core.AbstractUnifyComponent;
import com.tcdng.unify.core.ApplicationComponents;
import com.tcdng.unify.core.UnifyCoreErrorConstants;
import com.tcdng.unify.core.UnifyException;
import com.tcdng.unify.core.annotation.Component;
import com.tcdng.unify.core.data.FactoryMap;
import com.tcdng.unify.core.database.NativeQuery;

/**
 * Default implementation of dynamic SQL data source manager.
 * 
 * @author Lateef Ojulari
 * @since 1.0
 */
@Component(ApplicationComponents.APPLICATION_DYNAMICSQLDATASOURCEMANAGER)
public class DynamicSqlDataSourceManagerImpl extends AbstractUnifyComponent implements DynamicSqlDataSourceManager {

    private FactoryMap<String, DynamicSqlDataSource> dynamicSqlDataSoureMap;

    public DynamicSqlDataSourceManagerImpl() {
        dynamicSqlDataSoureMap = new FactoryMap<String, DynamicSqlDataSource>() {
            @Override
            protected DynamicSqlDataSource create(String key, Object... params) throws Exception {
                return newDynamicSqlDataSource((DynamicSqlDataSourceConfig) params[0]);
            }
        };
    }

    @Override
    public void configure(DynamicSqlDataSourceConfig dynamicSqlDataSourceConfig) throws UnifyException {
        String configName = dynamicSqlDataSourceConfig.getName();
        if (dynamicSqlDataSoureMap.isKey(configName)) {
            throw new UnifyException(UnifyCoreErrorConstants.DYNAMIC_DATASOURCE_ALREADY_CONFIGURED, configName);
        }

        dynamicSqlDataSoureMap.get(configName, dynamicSqlDataSourceConfig);
    }

    @Override
    public boolean reconfigure(DynamicSqlDataSourceConfig dynamicSqlDataSourceConfig) throws UnifyException {
        String configName = dynamicSqlDataSourceConfig.getName();
        if (dynamicSqlDataSoureMap.isKey(configName)) {
            dynamicSqlDataSoureMap.get(configName).reconfigure(dynamicSqlDataSourceConfig);
            return true;
        }

        return false;
    }

    @Override
    public boolean testConfiguration(DynamicSqlDataSourceConfig dynamicSqlDataSourceConfig) throws UnifyException {
        DynamicSqlDataSource dynamicSqlDataSource = newDynamicSqlDataSource(dynamicSqlDataSourceConfig);
        try {
            return dynamicSqlDataSource.testConnection();
        } finally {
            dynamicSqlDataSource.terminate();
        }
    }

    @Override
    public int testNativeQuery(DynamicSqlDataSourceConfig dynamicSqlDataSourceConfig, NativeQuery query)
            throws UnifyException {
        DynamicSqlDataSource dynamicSqlDataSource = newDynamicSqlDataSource(dynamicSqlDataSourceConfig);
        try {
            return dynamicSqlDataSource.testNativeQuery(query);
        } finally {
            dynamicSqlDataSource.terminate();
        }
    }

    @Override
    public int testNativeQuery(DynamicSqlDataSourceConfig dynamicSqlDataSourceConfig, String nativeSql)
            throws UnifyException {
        DynamicSqlDataSource dynamicSqlDataSource = newDynamicSqlDataSource(dynamicSqlDataSourceConfig);
        try {
            return dynamicSqlDataSource.testNativeQuery(nativeSql);
        } finally {
            dynamicSqlDataSource.terminate();
        }
    }

    @Override
    public int testNativeUpdate(DynamicSqlDataSourceConfig dynamicSqlDataSourceConfig, String updateSql)
            throws UnifyException {
        DynamicSqlDataSource dynamicSqlDataSource = newDynamicSqlDataSource(dynamicSqlDataSourceConfig);
        try {
            return dynamicSqlDataSource.testNativeUpdate(updateSql);
        } finally {
            dynamicSqlDataSource.terminate();
        }
    }

    @Override
    public boolean isConfigured(String configName) throws UnifyException {
        if (dynamicSqlDataSoureMap.isKey(configName)) {
            return dynamicSqlDataSoureMap.get(configName).isConfigured();
        }
        return false;
    }

    @Override
    public int getDataSourceCount() throws UnifyException {
        return dynamicSqlDataSoureMap.size();
    }

    @Override
    public List<String> getSchemas(String configName) throws UnifyException {
        return getDynamicSqlDataSource(configName).getSchemaList();
    }

    @Override
    public List<SqlTableInfo> getTables(String configName, String schemaName, SqlTableType sqlTableType)
            throws UnifyException {
        return getDynamicSqlDataSource(configName).getTableList(schemaName, sqlTableType);
    }

    @Override
    public List<SqlColumnInfo> getColumns(String configName, String schemaName, String tableName)
            throws UnifyException {
        return getDynamicSqlDataSource(configName).getColumnList(schemaName, tableName);
    }

    @Override
    public List<Object[]> getRows(String configName, NativeQuery query) throws UnifyException {
        return getDynamicSqlDataSource(configName).getRows(query);
    }

    @Override
    public SqlDataSource getDataSource(String configName) throws UnifyException {
        return getDynamicSqlDataSource(configName);
    }

    @Override
    public Connection getConnection(String configName) throws UnifyException {
        return getDynamicSqlDataSource(configName).getConnection();
    }

    @Override
    public boolean restoreConnection(String configName, Connection connection) throws UnifyException {
        return getDynamicSqlDataSource(configName).restoreConnection(connection);
    }

    @Override
    public void terminateConfiguration(String configName) throws UnifyException {
        DynamicSqlDataSource dynamicSqlDataSource = getDynamicSqlDataSource(configName);
        try {
            dynamicSqlDataSource.terminate();
        } finally {
            dynamicSqlDataSoureMap.remove(configName);
        }
    }

    @Override
    public void terminateAll() throws UnifyException {
        for (String configName : new ArrayList<String>(dynamicSqlDataSoureMap.keySet())) {
            terminateConfiguration(configName);
        }
    }

    @Override
    protected void onInitialize() throws UnifyException {

    }

    @Override
    protected void onTerminate() throws UnifyException {
        terminateAll();
    }

    private DynamicSqlDataSource getDynamicSqlDataSource(String configName) throws UnifyException {
        if (!dynamicSqlDataSoureMap.isKey(configName)) {
            throw new UnifyException(UnifyCoreErrorConstants.DYNAMIC_DATASOURCE_IS_UNKNOWN, configName);
        }

        return dynamicSqlDataSoureMap.get(configName);
    }

    private DynamicSqlDataSource newDynamicSqlDataSource(DynamicSqlDataSourceConfig dynamicSqlDataSourceConfig)
            throws UnifyException {
        DynamicSqlDataSource dynamicSqlDataSource =
                (DynamicSqlDataSource) getComponent(ApplicationComponents.APPLICATION_DYNAMICSQLDATASOURCE);
        dynamicSqlDataSource.configure(dynamicSqlDataSourceConfig);
        return dynamicSqlDataSource;
    }
}
