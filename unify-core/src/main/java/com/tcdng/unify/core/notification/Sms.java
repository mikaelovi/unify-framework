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

package com.tcdng.unify.core.notification;

import com.tcdng.unify.core.UnifyException;
import com.tcdng.unify.core.util.ErrorUtils;
import com.tcdng.unify.core.util.StringUtils;

/**
 * An SMS object.
 * 
 * @author Lateef Ojulari
 * @since 1.0
 */
public class Sms {

    private String id;

    private String sender;

    private String recipient;

    private String message;

    private boolean sent;

    public Sms(String id, String sender, String recipient, String message) {
        this.id = id;
        this.sender = sender;
        this.recipient = recipient;
        this.message = message;
    }

    public String getId() {
        return id;
    }

    public String getSender() {
        return sender;
    }

    public String getReciever() {
        return recipient;
    }

    public String getMessage() {
        return message;
    }

    public boolean isSent() {
        return sent;
    }

    public void setSent(boolean sent) {
        this.sent = sent;
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static class Builder {

        private String id;

        private String sender;

        private String recipient;

        private String message;

        private Builder() {

        }

        public Builder withId(String id) {
            this.id = id;
            return this;
        }

        public Builder fromSender(String sender) {
            this.sender = sender;
            return this;
        }

        public Builder toRecipient(String recipient) {
            this.recipient = recipient;
            return this;
        }

        public Builder containingMessage(String message) {
            this.message = message;
            return this;
        }

        public Sms build() throws UnifyException {
            if (StringUtils.isBlank(sender)) {
                ErrorUtils.throwBuildError("SMS sender is required");
            }

            if (StringUtils.isBlank(recipient)) {
                ErrorUtils.throwBuildError("SMS recipient is required");
            }

            if (StringUtils.isBlank(message)) {
                ErrorUtils.throwBuildError("SMS message is required");
            }

            return new Sms(id, sender, recipient, message);
        }
    }
}
