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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

import com.tcdng.unify.core.AbstractUnifyComponentTest;
import com.tcdng.unify.core.ApplicationComponents;

/**
 * Dynamic SQL database manager test.
 * 
 * @author Lateef Ojulari
 * @since 1.0
 */
public class DynamicSqlDatabaseManagerTest extends AbstractUnifyComponentTest {

    private static final String TEST_CONFIG = "test-config.PUBLIC";

    @Test
    public void testGetDynamicSqlDatabase() throws Exception {
        DynamicSqlDatabaseManager dynamicSqlDatabaseManager =
                (DynamicSqlDatabaseManager) getComponent(ApplicationComponents.APPLICATION_DYNAMICSQLDATABASEMANAGER);
        DynamicSqlDatabase db = dynamicSqlDatabaseManager.getDynamicSqlDatabase(TEST_CONFIG);
        assertNotNull(db);
        assertEquals(ApplicationComponents.APPLICATION_DYNAMICSQLDATASOURCE, db.getDataSourceName());
    }

    @Override
    protected void onSetup() throws Exception {
        // Configure data source
        DynamicSqlDataSourceManager dynamicSqlDataSourceManager = (DynamicSqlDataSourceManager) getComponent(
                ApplicationComponents.APPLICATION_DYNAMICSQLDATASOURCEMANAGER);
        dynamicSqlDataSourceManager.configure(new DynamicSqlDataSourceConfig(TEST_CONFIG, "hsqldb-dialect",
                "org.hsqldb.jdbcDriver", "jdbc:hsqldb:mem:dyntest", null, null, 2, true));
    }

    @Override
    protected void onTearDown() throws Exception {
        // Unconfigure data source
        DynamicSqlDataSourceManager dynamicSqlDataSourceManager = (DynamicSqlDataSourceManager) getComponent(
                ApplicationComponents.APPLICATION_DYNAMICSQLDATASOURCEMANAGER);
        if (dynamicSqlDataSourceManager.isConfigured(TEST_CONFIG)) {
            dynamicSqlDataSourceManager.terminateAll();
        }
    }
}
