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
package com.tcdng.unify.web.http;

import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.Set;

import com.tcdng.unify.core.UnifyException;
import com.tcdng.unify.web.AbstractClientRequest;
import com.tcdng.unify.web.ClientRequestType;
import com.tcdng.unify.web.PathParts;
import com.tcdng.unify.web.UnifyWebErrorConstants;
import com.tcdng.unify.web.constant.RequestParameterConstants;

/**
 * HTTP client request.
 * 
 * @author Lateef Ojulari
 * @since 1.0
 */
public class HttpClientRequest extends AbstractClientRequest {

    private HttpRequestMethodType methodType;

    private PathParts pathParts;

    private Charset charset;

    private Map<String, Object> parameters;

    public HttpClientRequest(HttpRequestMethodType methodType, PathParts pathParts, Charset charset,
            Map<String, Object> parameters) {
        this.pathParts = pathParts;
        this.charset = charset;
        this.parameters = parameters;
        this.methodType = methodType;
    }

    @Override
    public ClientRequestType getType() {
        return methodType.clientRequestType();
    }

    @Override
    public PathParts getPathParts() {
        return pathParts;
    }

    @Override
    public Charset getCharset() {
        return charset;
    }

    @Override
    public Set<String> getParameterNames() {
        return parameters.keySet();
    }

    @Override
    public Object getParameter(String name) {
        return parameters.get(name);
    }

    @Override
    public InputStream getInputStream() throws UnifyException {
        InputStream in = (InputStream) parameters.get(RequestParameterConstants.REMOTE_CALL_INPUTSTREAM);
        if (in == null) {
            throw new UnifyException(UnifyWebErrorConstants.REMOTECALL_NOT_INPUTSTREAM);
        }

        return in;
    }
}
