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

package com.tcdng.unify.core.database.sql;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.tcdng.unify.core.UnifyCoreErrorConstants;
import com.tcdng.unify.core.UnifyException;
import com.tcdng.unify.core.util.DataUtils;

/**
 * Holds callable information.
 * 
 * @author Lateef Ojulari
 * @since 1.0
 */
public class SqlCallableInfo {

    private Class<?> callableClass;

    private String procedureName;

    private String preferredProcedureName;

    private String schemaProcedureName;

    private List<SqlCallableParamInfo> paramInfoList;

    private Map<Class<?>, SqlCallableResultInfo> resultInfoByType;

    public SqlCallableInfo(Class<?> callableClass, String procedureName, String preferredProcedureName, String schemaProcedureName,
            List<SqlCallableParamInfo> paramInfoList, List<SqlCallableResultInfo> resultInfoList) {
        this.callableClass = callableClass;
        this.procedureName = procedureName;
        this.preferredProcedureName = preferredProcedureName;
        this.schemaProcedureName = schemaProcedureName;
        this.paramInfoList = DataUtils.unmodifiableList(paramInfoList);
        this.resultInfoByType = Collections.emptyMap();
        if(!DataUtils.isBlank(resultInfoList)) {
            Map<Class<?>, SqlCallableResultInfo> map = new HashMap<Class<?>, SqlCallableResultInfo>();
            for (SqlCallableResultInfo sqlCallableResultInfo: resultInfoList) {
                map.put(sqlCallableResultInfo.getCallableResultClass(), sqlCallableResultInfo);
            }
            
            this.resultInfoByType = DataUtils.unmodifiableMap(map);
        }
    }

    public Class<?> getCallableClass() {
        return callableClass;
    }

    public String getProcedureName() {
        return procedureName;
    }

    public String getPreferredProcedureName() {
        return preferredProcedureName;
    }

    public String getSchemaProcedureName() {
        return schemaProcedureName;
    }

    public List<SqlCallableParamInfo> getParamInfoList() {
        return paramInfoList;
    }

    public boolean isParams() {
        return !paramInfoList.isEmpty();
    }
    
    public Collection<Class<?>> getResultTypes() {
        return resultInfoByType.keySet();
    }
    
    public SqlCallableResultInfo getResultInfo(Class<?> type) throws UnifyException {
        SqlCallableResultInfo sqlCallableResultInfo = resultInfoByType.get(type);
        if (sqlCallableResultInfo == null) {
            throw new UnifyException(UnifyCoreErrorConstants.CALLABLE_RESULT_TYPE_NOT_FOUND, callableClass, type);
        }

        return sqlCallableResultInfo;
    }
}
