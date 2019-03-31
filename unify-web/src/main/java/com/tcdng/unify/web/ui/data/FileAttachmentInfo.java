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
package com.tcdng.unify.web.ui.data;

import com.tcdng.unify.core.constant.FileAttachmentType;

/**
 * File attachment information.
 * 
 * @author Lateef Ojulari
 * @since 1.0
 */
public class FileAttachmentInfo {

    private String name;

    private String description;

    private String filename;

    private FileAttachmentType type;

    private byte[] attachment;

    public FileAttachmentInfo(String name, String description, FileAttachmentType type) {
        this.name = name;
        this.description = description;
        this.type = type;
    }

    public FileAttachmentInfo(FileAttachmentType type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public FileAttachmentType getType() {
        return type;
    }

    public String getTypeName() {
        return type.code();
    }

    public String getAccept() {
        return type.mimeType().template();
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public byte[] getAttachment() {
        return attachment;
    }

    public void setAttachment(byte[] attachment) {
        this.attachment = attachment;
    }

    public boolean isEmpty() {
        return this.filename == null;
    }

    public int size() {
        if (this.attachment != null) {
            return this.attachment.length;
        }

        return 0;
    }
}
