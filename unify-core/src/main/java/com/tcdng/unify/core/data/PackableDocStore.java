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
package com.tcdng.unify.core.data;

import com.tcdng.unify.core.UnifyException;
import com.tcdng.unify.core.format.Formatter;
import com.tcdng.unify.core.util.DataUtils;
import com.tcdng.unify.core.util.ReflectUtils;

/**
 * Packable document value store.
 * 
 * @author Lateef Ojulari
 * @since 1.0
 */
public class PackableDocStore extends AbstractValueStore<PackableDoc> {

    public PackableDocStore(PackableDoc packableDoc) {
        this(packableDoc, -1);
    }

    public PackableDocStore(PackableDoc packableDoc, int dataIndex) {
        super(packableDoc, dataIndex);
    }

    @Override
    public boolean isGettable(String name) throws UnifyException {
        if (name.startsWith(PackableDoc.RESERVED_EXT_FIELD)) {
            int len = PackableDoc.RESERVED_EXT_FIELD.length();
            if (name.length() == len) {
                return true;
            }

            if (name.charAt(len) == '.') {
                Object resrvExt = storage.getResrvExt();
                if (resrvExt != null) {
                    return ReflectUtils.isGettableField(resrvExt.getClass(), name.substring(len + 1));
                }
            }
        }
        
        return storage != null && storage.isField(name);
    }

    @Override
    public boolean isSettable(String name) throws UnifyException {
        if (name.startsWith(PackableDoc.RESERVED_EXT_FIELD)) {
            int len = PackableDoc.RESERVED_EXT_FIELD.length();
            if (name.length() == len) {
                return true;
            }

            if (name.charAt(len) == '.') {
                Object resrvExt = storage.getResrvExt();
                if (resrvExt != null) {
                    return ReflectUtils.isSettableField(resrvExt.getClass(), name.substring(len + 1));
                }
            }
        }
        
        return storage != null && storage.isField(name);
    }

    @Override
    protected Object doRetrieve(String property) throws UnifyException {
        if (property.startsWith(PackableDoc.RESERVED_EXT_FIELD)) {
            int len = PackableDoc.RESERVED_EXT_FIELD.length();
            if (property.length() == len) {
                return storage.getResrvExt();
            }

            if (property.charAt(len) == '.') {
                return ReflectUtils.findNestedBeanProperty(storage.getResrvExt(), property.substring(len + 1));
            }
        }

        return storage.readField(property);
    }

    @Override
    protected void doStore(String property, Object value, Formatter<?> formatter) throws UnifyException {
        if (property.startsWith(PackableDoc.RESERVED_EXT_FIELD)) {
            int len = PackableDoc.RESERVED_EXT_FIELD.length();
            if (property.length() == len) {
                storage.setResrvExt(value);
                return;
            }

            if (property.charAt(len) == '.') {
                DataUtils.setNestedBeanProperty(storage.getResrvExt(), property.substring(len + 1), value, formatter);
                return;
            }
        }

        storage.writeField(property, value, formatter);
    }

}
