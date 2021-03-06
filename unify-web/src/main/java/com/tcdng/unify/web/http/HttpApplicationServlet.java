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

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.TimeZone;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.tcdng.unify.core.ApplicationComponents;
import com.tcdng.unify.core.RequestContextManager;
import com.tcdng.unify.core.UnifyContainer;
import com.tcdng.unify.core.UnifyContainerConfig;
import com.tcdng.unify.core.UnifyContainerEnvironment;
import com.tcdng.unify.core.UnifyCoreSessionAttributeConstants;
import com.tcdng.unify.core.UnifyException;
import com.tcdng.unify.core.UserSession;
import com.tcdng.unify.core.UserToken;
import com.tcdng.unify.core.constant.UserPlatform;
import com.tcdng.unify.core.system.UserSessionManager;
import com.tcdng.unify.core.upl.UplComponentWriterManager;
import com.tcdng.unify.core.util.ColorUtils;
import com.tcdng.unify.core.util.IOUtils;
import com.tcdng.unify.core.util.StringUtils;
import com.tcdng.unify.core.util.TypeRepository;
import com.tcdng.unify.core.util.UnifyConfigUtils;
import com.tcdng.unify.web.PathParts;
import com.tcdng.unify.web.UnifyWebInterface;
import com.tcdng.unify.web.UnifyWebPropertyConstants;
import com.tcdng.unify.web.WebApplicationComponents;
import com.tcdng.unify.web.constant.RequestParameterConstants;
import com.tcdng.unify.web.constant.ReservedPageControllerConstants;
import com.tcdng.unify.web.util.WebTypeUtils;

/**
 * Serves as an interface between the servlet container and the Unify container;
 * handling the exchange of http requests and responses between both containers.
 * 
 * @author Lateef Ojulari
 * @since 1.0
 */
@MultipartConfig
public class HttpApplicationServlet extends HttpServlet {

    /** The serial version ID */
    private static final long serialVersionUID = 3971544226497014269L;

    private static final String CONFIGURATION_FILE = "conf/unify.xml";

    private UnifyContainer unifyContainer;

    private UnifyWebInterface webInterface;

    private RequestContextManager requestContextManager;

    private HttpRequestHandler httpRequestHandler;

    private UserSessionManager userSessionManager;

    private UplComponentWriterManager uplComponentWriterManager;

    private Locale applicationLocale;

    private TimeZone applicationTimeZone;

    private String contextPath;

    private boolean embedded;

    public HttpApplicationServlet() {
        this(false);
    }

    public HttpApplicationServlet(boolean embedded) {
        this.embedded = embedded;
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);

        if (!embedded) {
            ServletContext servletContext = config.getServletContext();
            String workingFolder = servletContext.getRealPath("");
            String configFilename = config.getInitParameter("application-config-file");
            if (StringUtils.isBlank(configFilename)) {
                configFilename = IOUtils.buildFilename(workingFolder, CONFIGURATION_FILE);
            }

            InputStream configInputStream = null;
            try {
                contextPath = servletContext.getContextPath();
                TypeRepository tr = WebTypeUtils.getTypeRepositoryFromServletContext(servletContext);
                UnifyContainerEnvironment uce = new UnifyContainerEnvironment(tr, workingFolder);
                UnifyContainerConfig.Builder uccb = UnifyContainerConfig.newBuilder();

                // Scan configuration
                UnifyConfigUtils.readConfigFromTypeRepository(uccb, tr);
                uccb.deploymentMode(true);
                configInputStream = new FileInputStream(IOUtils.fileInstance(configFilename, null));

                // Read xml configuration
                UnifyConfigUtils.readConfigFromXml(uccb, configInputStream);
                uccb.setPropertyIfBlank(UnifyWebPropertyConstants.APPLICATION_HOME,
                        ReservedPageControllerConstants.DEFAULT_APPLICATION_HOME);

                // Create container
                UnifyContainerConfig ucc = uccb.build();
                unifyContainer = new UnifyContainer();
                unifyContainer.startup(uce, ucc);
                applicationLocale = unifyContainer.getApplicationLocale();
                applicationTimeZone = unifyContainer.getApplicationTimeZone();
                requestContextManager = (RequestContextManager) unifyContainer
                        .getComponent(ApplicationComponents.APPLICATION_REQUESTCONTEXTMANAGER);
                httpRequestHandler = (HttpRequestHandler) unifyContainer
                        .getComponent(WebApplicationComponents.APPLICATION_HTTPREQUESTHANDLER);
                userSessionManager = (UserSessionManager) unifyContainer
                        .getComponent(ApplicationComponents.APPLICATION_USERSESSIONMANAGER);
                uplComponentWriterManager = (UplComponentWriterManager) unifyContainer
                        .getComponent(ApplicationComponents.APPLICATION_UPLCOMPONENTWRITERMANAGER);
            } catch (Exception e) {
                e.printStackTrace();
                if (unifyContainer != null && unifyContainer.isStarted()) {
                    unifyContainer.shutdown();
                }
                throw new ServletException(e);
            } finally {
                IOUtils.close(configInputStream);
            }
        }
    }

    @Override
    public void destroy() {
        if (unifyContainer != null && unifyContainer.isStarted()) {
            unifyContainer.shutdown();
        }
        super.destroy();
    }

    public void setup(Locale applicationLocale, TimeZone applicationTimeZone, UnifyWebInterface webInterface,
            RequestContextManager requestContextManager, HttpRequestHandler applicationController,
            UserSessionManager userSessionManager, UplComponentWriterManager uplComponentWriterManager) {
        this.applicationLocale = applicationLocale;
        this.applicationTimeZone = applicationTimeZone;
        this.webInterface = webInterface;
        this.requestContextManager = requestContextManager;
        this.httpRequestHandler = applicationController;
        this.userSessionManager = userSessionManager;
        this.uplComponentWriterManager = uplComponentWriterManager;
        contextPath = webInterface.getContextPath();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        doRequestMethod(HttpRequestMethodType.GET, request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        doRequestMethod(HttpRequestMethodType.POST, request, response);
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        doRequestMethod(HttpRequestMethodType.DELETE, request, response);
    }

    @Override
    protected void doHead(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        doRequestMethod(HttpRequestMethodType.HEAD, request, response);
    }

    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        doRequestMethod(HttpRequestMethodType.OPTIONS, request, response);
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        doRequestMethod(HttpRequestMethodType.PUT, request, response);
    }

    @Override
    protected void doTrace(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        doRequestMethod(HttpRequestMethodType.TRACE, request, response);
    }

    private void doRequestMethod(HttpRequestMethodType type, HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if (!embedded || webInterface.isServicingRequests()) {
            try {
                PathParts reqPathParts = httpRequestHandler.resolveRequestPath(request);
                requestContextManager.loadRequestContext(getUserSession(request, reqPathParts.isUiController()),
                        request.getServletPath());
                httpRequestHandler.handleRequest(type, reqPathParts, request, response);
            } catch (Exception e) {
                throw new ServletException(e);
            } finally {
                try {
                    userSessionManager.updateCurrentSessionLastAccessTime();
                } catch (Exception e) {
                }
                requestContextManager.unloadRequestContext();
            }
        }
    }

    private UserSession getUserSession(HttpServletRequest request, boolean isUIController) throws UnifyException {
        HttpUserSession userSession = null;
        if (!isUIController) {
            // Non-UI controllers are session less. Handle sessionless remote call
            HttpSession httpSession = request.getSession(false);
            if (httpSession != null) {
                httpSession.invalidate();
            }
            // Create single use session object
            userSession = createHttpUserSession(request, null);
        } else {
            if (StringUtils.isNotBlank(request.getParameter(RequestParameterConstants.REMOTE_VIEWER))) {
                // Handle remote view
                HttpSession httpSession = request.getSession(false);
                if (httpSession != null) {
                    httpSession.invalidate();
                }

                String sessionId = (String) request.getParameter(RequestParameterConstants.REMOTE_SESSION_ID);
                userSession = (HttpUserSession) userSessionManager.getUserSession(sessionId);
                if (userSession == null) {
                    userSession = createHttpUserSession(request, sessionId);
                    userSessionManager.addUserSession(userSession);

                    String userLoginId = request.getParameter(RequestParameterConstants.REMOTE_USERLOGINID);
                    String userName = request.getParameter(RequestParameterConstants.REMOTE_USERNAME);
                    String roleCode = request.getParameter(RequestParameterConstants.REMOTE_ROLECD);
                    String branchCode = request.getParameter(RequestParameterConstants.REMOTE_BRANCH_CODE);
                    String zoneCode = request.getParameter(RequestParameterConstants.REMOTE_ZONE_CODE);
                    String tenantCode = request.getParameter(RequestParameterConstants.REMOTE_TENANT_CODE);
                    String colorScheme = ColorUtils.getConformingColorSchemeCode(
                            request.getParameter(RequestParameterConstants.REMOTE_COLOR_SCHEME));
                    boolean globalAccess =
                            Boolean.valueOf(request.getParameter(RequestParameterConstants.REMOTE_GLOBAL_ACCESS));
                    UserToken userToken = new UserToken(userLoginId, userName, userSession.getRemoteAddress(),
                            branchCode, zoneCode, tenantCode, colorScheme, globalAccess, true, true, true);
                    userToken.setRoleCode(roleCode);
                    userSession.getSessionContext().setUserToken(userToken);
                }
            } else {
                // Handle document request
                HttpSession httpSession = request.getSession();
                userSession = (HttpUserSession) httpSession.getAttribute(HttpConstants.USER_SESSION);
                if (userSession == null) {
                    synchronized (httpSession) {
                        userSession = (HttpUserSession) httpSession.getAttribute(HttpConstants.USER_SESSION);
                        if (userSession == null) {
                            userSession = createHttpUserSession(request, null);
                            httpSession.setAttribute(HttpConstants.USER_SESSION, userSession);
                        }
                    }
                }
            }
        }

        userSession.setTransient(userSessionManager);
        return userSession;
    }

    private HttpUserSession createHttpUserSession(HttpServletRequest request, String sessionId) throws UnifyException {
        String remoteIpAddress = request.getHeader("X-FORWARDED-FOR");
        if (remoteIpAddress == null || remoteIpAddress.trim().isEmpty()
                || "unknown".equalsIgnoreCase(remoteIpAddress)) {
            remoteIpAddress = request.getHeader("Proxy-Client-IP");
        }

        if (remoteIpAddress == null || remoteIpAddress.trim().isEmpty()
                || "unknown".equalsIgnoreCase(remoteIpAddress)) {
            remoteIpAddress = request.getHeader("WL-Proxy-Client-IP");
        }

        if (remoteIpAddress == null || remoteIpAddress.trim().isEmpty()
                || "unknown".equalsIgnoreCase(remoteIpAddress)) {
            remoteIpAddress = request.getRemoteAddr();
        }

        StringBuilder uriBase = new StringBuilder();
        uriBase.append(request.getScheme()).append("://").append(request.getServerName());
        if (!(("http".equals(request.getScheme()) && request.getServerPort() == 80)
                || ("https".equals(request.getScheme()) && request.getServerPort() == 443))) {
            uriBase.append(":").append(request.getServerPort());
        }

        UserPlatform platform = detectRequestPlatform(request);
        HttpUserSession userSession =
                new HttpUserSession(applicationLocale, applicationTimeZone, sessionId, uriBase.toString(), contextPath,
                        request.getRemoteHost(), remoteIpAddress, request.getRemoteUser(), platform);
        userSession.getSessionContext().setStickyAttribute(UnifyCoreSessionAttributeConstants.UPLCOMPONENT_WRITERS,
                uplComponentWriterManager.getWriters(platform));
        userSession.setTransient(userSessionManager);
        return userSession;
    }

    private UserPlatform detectRequestPlatform(HttpServletRequest request) {
        UserPlatform platform = UserPlatform.DEFAULT;
        String userAgent = request.getHeader("User-Agent");
        if (userAgent != null && userAgent.indexOf("Mobile") >= 0) {
            platform = UserPlatform.MOBILE;
        }
        return platform;
    }
}
