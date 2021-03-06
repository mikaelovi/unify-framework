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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.tcdng.unify.core.UnifyCoreErrorConstants;
import com.tcdng.unify.core.UnifyException;
import com.tcdng.unify.core.constant.EnumConst;
import com.tcdng.unify.core.database.Entity;
import com.tcdng.unify.core.database.EntityPolicy;
import com.tcdng.unify.core.util.DataUtils;
import com.tcdng.unify.core.util.StringUtils;

/**
 * Holds entity information.
 * 
 * @author Lateef Ojulari
 * @since 1.0
 */
public class SqlEntityInfo implements SqlEntitySchemaInfo {

    private Long index;

    private Class<? extends Entity> entityClass;

    private Class<? extends EnumConst> enumConstClass;

    private EntityPolicy entityPolicy;

    private String schema;
    
    private String tableName;

    private String preferredTableName;

    private String schemaTableName;

    private String tableAlias;

    private String viewName;

    private String preferredViewName;

    private String schemaViewName;

    private SqlFieldInfo idFieldInfo;

    private SqlFieldInfo versionFieldInfo;

    private List<SqlFieldInfo> fieldInfoList;

    private List<SqlFieldInfo> listFieldInfoList;

    private Map<String, SqlFieldInfo> fieldInfoByName;

    private Map<String, SqlFieldInfo> listFieldInfoByName;

    private Map<Long, SqlFieldInfo> listInfoMapByIndex;

    private List<SqlForeignKeyInfo> foreignKeyList;

    private Map<String, ChildFieldInfo> childInfoByName;

    private List<ChildFieldInfo> childInfoList;

    private List<ChildFieldInfo> childListInfoList;

    private List<OnDeleteCascadeInfo> onDeleteCascadeInfoList;

    private Map<String, SqlUniqueConstraintInfo> uniqueConstraintMap;

    private Map<String, SqlIndexInfo> indexMap;

    private List<Map<String, Object>> staticValueList;

    private Map<String, Class<?>> viewBaseTables;

    private List<SqlViewRestrictionInfo> viewRestrictionList;

    public SqlEntityInfo(Long index, Class<? extends Entity> entityClass, Class<? extends EnumConst> enumConstClass,
            EntityPolicy recordPolicy, String schema, String tableName, String preferredTableName, String schemaTableName,
            String tableAlias, String viewName, String preferredViewName, String schemaViewName,
            SqlFieldInfo idFieldInfo, SqlFieldInfo versionFieldInfo, Map<String, SqlFieldInfo> sQLFieldInfoMap,
            List<ChildFieldInfo> childInfoList, List<ChildFieldInfo> childListInfoList,
            Map<String, SqlUniqueConstraintInfo> uniqueConstraintMap, Map<String, SqlIndexInfo> indexMap,
            List<Map<String, Object>> staticValueList, Map<String, Class<?>> viewBaseTables,
            List<SqlViewRestrictionInfo> viewRestrictionList, boolean isAllObjectsInLowerCase) throws UnifyException {
        this.index = index;
        this.entityClass = entityClass;
        this.enumConstClass = enumConstClass;
        this.entityPolicy = recordPolicy;
        this.schema = schema;
        this.tableName = tableName;
        this.preferredTableName = preferredTableName;
        this.schemaTableName = schemaTableName;
        this.tableAlias = tableAlias;
        this.viewName = viewName;
        this.preferredViewName = preferredViewName;
        this.schemaViewName = schemaViewName;
        this.idFieldInfo = idFieldInfo;
        this.versionFieldInfo = versionFieldInfo;
        this.fieldInfoList = new ArrayList<SqlFieldInfo>();
        this.listFieldInfoList = new ArrayList<SqlFieldInfo>();
        this.listFieldInfoByName = Collections.unmodifiableMap(sQLFieldInfoMap);
        this.foreignKeyList = new ArrayList<SqlForeignKeyInfo>();
        this.fieldInfoByName = new HashMap<String, SqlFieldInfo>();

        if (isAllObjectsInLowerCase) {
            this.tableName = StringUtils.toLowerCase(tableName);
            this.preferredTableName = StringUtils.toLowerCase(preferredTableName);
            this.schemaTableName = StringUtils.toLowerCase(schemaTableName);
            this.tableAlias = StringUtils.toLowerCase(tableAlias);
            this.viewName = StringUtils.toLowerCase(viewName);
            this.preferredViewName = StringUtils.toLowerCase(preferredViewName);
            this.schemaViewName = StringUtils.toLowerCase(schemaViewName);
        }

        List<SqlFieldInfo> inputlistFieldInfoList = new ArrayList<SqlFieldInfo>(listFieldInfoByName.values());
        DataUtils.sort(inputlistFieldInfoList, SqlFieldSchemaInfo.class, "foreignEntityPreferredAlias", true);
        for (SqlFieldInfo sqlFieldInfo : inputlistFieldInfoList) {
            if (!sqlFieldInfo.isListOnly()) {
                fieldInfoByName.put(sqlFieldInfo.getName(), sqlFieldInfo);
                fieldInfoList.add(sqlFieldInfo);
                if (sqlFieldInfo.isForeignKey()) {
                    this.foreignKeyList.add(new SqlForeignKeyInfo(sqlFieldInfo));
                }
            }

            if (sqlFieldInfo.getMarker() != null) {
                if (this.listInfoMapByIndex == null) {
                    this.listInfoMapByIndex = new HashMap<Long, SqlFieldInfo>();
                }
                this.listInfoMapByIndex.put(sqlFieldInfo.getMarker(), sqlFieldInfo);
            }

            listFieldInfoList.add(sqlFieldInfo);
        }

        this.foreignKeyList = DataUtils.unmodifiableList(this.foreignKeyList);
        this.uniqueConstraintMap = DataUtils.unmodifiableMap(uniqueConstraintMap);
        this.indexMap = DataUtils.unmodifiableMap(indexMap);

        this.fieldInfoByName = DataUtils.unmodifiableMap(this.fieldInfoByName);
        this.fieldInfoList = DataUtils.unmodifiableList(this.fieldInfoList);
        this.listFieldInfoList = DataUtils.unmodifiableList(this.listFieldInfoList);

        this.childInfoList = DataUtils.unmodifiableList(childInfoList);
        this.childListInfoList = DataUtils.unmodifiableList(childListInfoList);
        this.childInfoByName = null;

        if (this.childInfoList.isEmpty() && this.childListInfoList.isEmpty()) {
            this.onDeleteCascadeInfoList = Collections.emptyList();
        } else {
            this.onDeleteCascadeInfoList = new ArrayList<OnDeleteCascadeInfo>();
            this.onDeleteCascadeInfoList.addAll(this.childInfoList);
            this.onDeleteCascadeInfoList.addAll(this.childListInfoList);
            this.onDeleteCascadeInfoList = Collections.unmodifiableList(this.onDeleteCascadeInfoList);

            this.childInfoByName = new HashMap<String, ChildFieldInfo>();
            for (OnDeleteCascadeInfo info : this.onDeleteCascadeInfoList) {
                ChildFieldInfo childFieldInfo = (ChildFieldInfo) info;
                this.childInfoByName.put(childFieldInfo.getName(), childFieldInfo);
            }
        }

        this.childInfoByName = DataUtils.unmodifiableMap(this.childInfoByName);
        this.staticValueList = staticValueList;
        this.viewBaseTables = DataUtils.unmodifiableMap(viewBaseTables);
        this.viewRestrictionList = DataUtils.unmodifiableList(viewRestrictionList);
    }

    @Override
    public Long getIndex() {
        return index;
    }

    @Override
    public String getSchema() {
        return schema;
    }

    @Override
    public String getTableName() {
        return tableName;
    }

    @Override
    public String getPreferredTableName() {
        return preferredTableName;
    }

    @Override
    public String getSchemaTableName() {
        return schemaTableName;
    }

    @Override
    public String getTableAlias() {
        return tableAlias;
    }

    @Override
    public String getViewName() {
        return viewName;
    }

    @Override
    public String getPreferredViewName() {
        return preferredViewName;
    }

    @Override
    public String getSchemaViewName() {
        return schemaViewName;
    }

    @Override
    public SqlFieldInfo getIdFieldInfo() {
        return idFieldInfo;
    }

    @Override
    public SqlFieldInfo getVersionFieldInfo() {
        return versionFieldInfo;
    }

    @Override
    public Set<String> getFieldNames() {
        return fieldInfoByName.keySet();
    }

    @Override
    public List<SqlFieldInfo> getFieldInfos() {
        return fieldInfoList;
    }

    @Override
    public List<SqlFieldInfo> getListFieldInfos() {
        return listFieldInfoList;
    }

    @Override
    public SqlFieldInfo getFieldInfo(String name) throws UnifyException {
        SqlFieldInfo sqlFieldInfo = fieldInfoByName.get(name);
        if (sqlFieldInfo == null) {
            throw new UnifyException(UnifyCoreErrorConstants.RECORD_FIELDINFO_NOT_FOUND, entityClass, name);
        }
        return sqlFieldInfo;
    }

    @Override
    public SqlFieldSchemaInfo getFieldInfo(Long index) throws UnifyException {
        return listInfoMapByIndex.get(index);
    }

    @Override
    public boolean isForeignKeys() {
        return !foreignKeyList.isEmpty();
    }

    @Override
    public boolean isUniqueConstraints() {
        return uniqueConstraintMap != null && !uniqueConstraintMap.isEmpty();
    }

    @Override
    public Map<String, SqlUniqueConstraintInfo> getUniqueConstraintList() {
        return uniqueConstraintMap;
    }

    @Override
    public boolean isIndexes() {
        return indexMap != null && !indexMap.isEmpty();
    }

    @Override
    public Map<String, SqlIndexInfo> getIndexList() {
        return indexMap;
    }

    @Override
    public List<Map<String, Object>> getStaticValueList() {
        return staticValueList;
    }

    @Override
    public boolean isViewOnly() {
        return !viewBaseTables.isEmpty();
    }

    @Override
    public Map<String, Class<?>> getViewBaseTables() {
        return viewBaseTables;
    }

    @Override
    public boolean isViewRestriction() {
        return !viewRestrictionList.isEmpty();
    }

    @Override
    public List<SqlViewRestrictionInfo> getViewRestrictionList() {
        return viewRestrictionList;
    }

    @Override
    public List<SqlForeignKeyInfo> getForeignKeyList() {
        return foreignKeyList;
    }

    public Class<? extends Entity> getEntityClass() {
        return entityClass;
    }

    public Class<? extends EnumConst> getEnumConstClass() {
        return enumConstClass;
    }

    public Class<?> getKeyClass() {
        if (enumConstClass != null) {
            return enumConstClass;
        }
        return entityClass;
    }

    public EntityPolicy getEntityPolicy() {
        return entityPolicy;
    }

    public Set<String> getListFieldNames() {
        return listFieldInfoByName.keySet();
    }

    public Map<String, String> getListColumnsByFieldNames() {
        Map<String, String> map = new HashMap<String, String>();
        for (Map.Entry<String, SqlFieldInfo> entry : listFieldInfoByName.entrySet()) {
            map.put(entry.getKey(), entry.getValue().getPreferredColumnName());
        }
        return map;
    }

    public Map<String, SqlFieldInfo> getFieldInfoByColumnNames() {
        Map<String, SqlFieldInfo> map = new HashMap<String, SqlFieldInfo>();
        for (SqlFieldInfo entry : fieldInfoByName.values()) {
            map.put(entry.getColumnName(), entry);
        }
        return map;
    }

    public ChildFieldInfo getChildFieldInfo(String name) {
        return childInfoByName.get(name);
    }

    public boolean isChildFieldInfo(String name) {
        return childInfoByName.containsKey(name);
    }

    public List<ChildFieldInfo> getSingleChildInfoList() {
        return childInfoList;
    }

    public boolean isSingleChildList() {
        return !childInfoList.isEmpty();
    }

    public List<ChildFieldInfo> getManyChildInfoList() {
        return childListInfoList;
    }

    public boolean isManyChildList() {
        return !childListInfoList.isEmpty();
    }

    public boolean isChildList() {
        return !childInfoList.isEmpty() || !childListInfoList.isEmpty();
    }

    public List<OnDeleteCascadeInfo> getOnDeleteCascadeInfoList() {
        return onDeleteCascadeInfoList;
    }

    public boolean isOnDeleteCascadeList() {
        return !onDeleteCascadeInfoList.isEmpty();
    }

    void expandOnDeleteCascade(OnDeleteCascadeInfo onDeleteCascadeInfo) {
        List<OnDeleteCascadeInfo> list = new ArrayList<OnDeleteCascadeInfo>(onDeleteCascadeInfoList);
        list.add(onDeleteCascadeInfo);
        onDeleteCascadeInfoList = Collections.unmodifiableList(list);
    }

    public boolean isField(String name) {
        return fieldInfoByName.containsKey(name);
    }

    public SqlFieldInfo getListFieldInfo(String name) throws UnifyException {
        SqlFieldInfo sqlFieldInfo = listFieldInfoByName.get(name);
        if (sqlFieldInfo == null) {
            throw new UnifyException(UnifyCoreErrorConstants.RECORD_LISTFIELDINFO_NOT_FOUND, entityClass, name);
        }
        return sqlFieldInfo;
    }

    public boolean isListField(String name) {
        return listFieldInfoByName.containsKey(name);
    }

    public boolean isVersioned() {
        return this.versionFieldInfo != null;
    }

    public boolean isViewable() {
        return !tableName.equals(viewName);
    }

    public boolean isEnumConst() {
        return enumConstClass != null;
    }

    public boolean testTrueFieldNamesOnly(Collection<String> fieldNames) {
        return fieldInfoByName.keySet().containsAll(fieldNames);
    }
}
