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
package com.tcdng.unify.core.constant;


import com.tcdng.unify.core.annotation.StaticList;
import com.tcdng.unify.core.util.EnumUtils;

/**
 * Simple data type constants.
 * 
 * @author Lateef Ojulari
 * @since 1.0
 */
@StaticList("simpledatatypelist")
public enum SimpleDataType implements EnumConst {

    CHAR(DataType.CHAR),
    BOOLEAN(DataType.BOOLEAN),
    SHORT(DataType.SHORT),
    INTEGER(DataType.INTEGER),
    LONG(DataType.LONG),
    FLOAT(DataType.FLOAT),
    DOUBLE(DataType.DOUBLE),
    DECIMAL(DataType.DECIMAL),
    DATE(DataType.DATE),
    STRING(DataType.STRING),
    BLOB(DataType.BLOB);

    private final DataType type;

    private SimpleDataType(DataType type) {
        this.type = type;
    }

    @Override
    public String code() {
        return type.code();
    }

    @Override
    public String defaultCode() {
        return type.defaultCode();
    }

    public DataType type() {
        return type;
    }

    public static SimpleDataType fromCode(String code) {
        return EnumUtils.fromCode(SimpleDataType.class, code);
    }

    public static SimpleDataType fromName(String name) {
        return EnumUtils.fromName(SimpleDataType.class, name);
    }
}
