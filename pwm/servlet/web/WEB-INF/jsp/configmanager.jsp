<%@ page import="password.pwm.http.bean.ConfigManagerBean" %>
<%@ page import="password.pwm.i18n.LocaleHelper" %>
<%@ page import="password.pwm.util.PwmLogLevel" %>
<%--
  ~ Password Management Servlets (PWM)
  ~ http://code.google.com/p/pwm/
  ~
  ~ Copyright (c) 2006-2009 Novell, Inc.
  ~ Copyright (c) 2009-2014 The PWM Project
  ~
  ~ This program is free software; you can redistribute it and/or modify
  ~ it under the terms of the GNU General Public License as published by
  ~ the Free Software Foundation; either version 2 of the License, or
  ~ (at your option) any later version.
  ~
  ~ This program is distributed in the hope that it will be useful,
  ~ but WITHOUT ANY WARRANTY; without even the implied warranty of
  ~ MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  ~ GNU General Public License for more details.
  ~
  ~ You should have received a copy of the GNU General Public License
  ~ along with this program; if not, write to the Free Software
  ~ Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
  --%>

<!DOCTYPE html>

<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<%
    final PwmApplication pwmApplication = ContextManager.getPwmApplication(session);
    final ConfigManagerBean configManagerBean = PwmSession.getPwmSession(session).getConfigManagerBean();
    String configFilePath = PwmConstants.DEFAULT_CONFIG_FILE_FILENAME;
    String pageTitle = LocaleHelper.getLocalizedMessage("Title_ConfigManager",pwmApplication.getConfig(),password.pwm.i18n.Config.class);
    try { configFilePath = ContextManager.getContextManager(session).getConfigReader().getConfigFile().toString(); } catch (Exception e) { /* */ }
%>
<html dir="<pwm:LocaleOrientation/>">
<%@ include file="fragment/header.jsp" %>
<body class="nihilo">
<div id="wrapper">
    <jsp:include page="fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="<%=pageTitle%>"/>
    </jsp:include>
    <div id="centerbody">
        <%@ include file="/WEB-INF/jsp/fragment/message.jsp" %>
        <div id="healthBody" style="border: #808080 1px solid; margin-top:5px; padding:0; max-height: 300px; overflow-y: auto">
            <div class="WaitDialogBlank"></div>
        </div>
        <pwm:script>
        <script type="text/javascript">
            PWM_GLOBAL['startupFunctions'].push(function(){
                require(["dojo/domReady!"],function(){
                    PWM_ADMIN.showAppHealth('healthBody', {showRefresh: true, showTimestamp: true});
                });
            });
        </script>
        </pwm:script>
        <style>
            .buttoncell {
                border: 0;
                width:50%;
            }
            .buttonrow {
                height: 45px;
                margin-top: 20px;
                margin-bottom: 20px;
            }
            .menubutton {
                cursor: pointer;
                display: block;
                margin-left: auto;
                margin-right: auto;

            }

        </style>
        <br/>
        <br/>
        <table style="border:0">
            <tr class="buttonrow">
                <td class="buttoncell">
                    <a class="menubutton" id="MenuItem_ConfigEditor">
                        <pwm:if test="showIcons"><span class="btn-icon fa fa-edit"></span></pwm:if>
                        <pwm:display key="MenuItem_ConfigEditor" bundle="Admin"/>
                    </a>
                    <pwm:script>
                    <script type="application/javascript">
                        PWM_GLOBAL['startupFunctions'].push(function(){
                            PWM_MAIN.addEventHandler('MenuItem_ConfigEditor','click',function(){PWM_CONFIG.startConfigurationEditor()});
                            makeTooltip('MenuItem_ConfigEditor',PWM_CONFIG.showString('MenuDisplay_ConfigEditor'));
                        });
                    </script>
                    </pwm:script>
                </td>
                <td class="buttoncell">
                    <a class="menubutton" onclick="PWM_CONFIG.openLogViewer(null)" id="MenuItem_ViewLog">
                        <pwm:if test="showIcons"><span class="btn-icon fa fa-list-alt"></span></pwm:if>
                        <pwm:display key="MenuItem_ViewLog" bundle="Config"/>
                    </a>
                    <pwm:script>
                    <script type="application/javascript">
                        PWM_GLOBAL['startupFunctions'].push(function(){
                            makeTooltip('MenuItem_ViewLog',PWM_CONFIG.showString('MenuDisplay_ViewLog'));
                        });
                    </script>
                    </pwm:script>
                </td>
            </tr>
            <tr class="buttonrow">
                <td class="buttoncell">
                    <a class="menubutton" onclick="downloadSupportBundle()" id="MenuItem_DownloadBundle">
                        <pwm:if test="showIcons"><span class="btn-icon fa fa-suitcase"></span></pwm:if>
                        <pwm:display key="MenuItem_DownloadBundle" bundle="Config"/>
                    </a>
                    <pwm:script>
                    <script type="application/javascript">
                        PWM_GLOBAL['startupFunctions'].push(function(){
                            makeTooltip('MenuItem_DownloadBundle',PWM_CONFIG.showString('MenuDisplay_DownloadBundle'));
                        });
                    </script>
                    </pwm:script>
                </td>
                <td class="buttoncell">
                    <a class="menubutton" onclick="downloadConfig()" id="MenuItem_DownloadConfig">
                        <pwm:if test="showIcons"><span class="btn-icon fa fa-download"></span></pwm:if>
                        <pwm:display key="MenuItem_DownloadConfig" bundle="Config"/>
                    </a>
                    <pwm:script>
                    <script type="application/javascript">
                        PWM_GLOBAL['startupFunctions'].push(function(){
                            makeTooltip('MenuItem_DownloadConfig',PWM_CONFIG.showString('MenuDisplay_DownloadConfig'));
                        });
                    </script>
                    </pwm:script>
                </td>
            </tr>
            <% if (!configManagerBean.isConfigLocked()) { %>
            <tr class="buttonrow">
                <td class="buttoncell">
                    <a class="menubutton" onclick="PWM_MAIN.showConfirmDialog({text:PWM_CONFIG.showString('MenuDisplay_UploadConfig'),okAction:function(){PWM_CONFIG.uploadConfigDialog()}})" id="MenuItem_UploadConfig">
                        <pwm:if test="showIcons"><span class="btn-icon fa fa-upload"></span></pwm:if>
                        <pwm:display key="MenuItem_UploadConfig" bundle="Config"/>
                    </a>
                    <pwm:script>
                    <script type="application/javascript">
                        PWM_GLOBAL['startupFunctions'].push(function(){
                            makeTooltip('MenuItem_UploadConfig',PWM_CONFIG.showString('MenuDisplay_UploadConfig'));
                        });
                    </script>
                    </pwm:script>
                </td>
                <td class="buttoncell">
                    <a class="menubutton" onclick="PWM_CONFIG.lockConfiguration()" id="MenuItem_LockConfig">
                        <pwm:if test="showIcons"><span class="btn-icon fa fa-lock"></span></pwm:if>
                        <pwm:display key="MenuItem_LockConfig" bundle="Config"/>
                    </a>
                    <pwm:script>
                    <script type="application/javascript">
                        PWM_GLOBAL['startupFunctions'].push(function(){
                            makeTooltip('MenuItem_LockConfig',PWM_CONFIG.showString('MenuDisplay_LockConfig',{value1:'<%=configFilePath%>'}));
                        });
                    </script>
                    </pwm:script>
                </td>
            </tr>
            <% } %>
            <tr class="buttonrow">
                <td class="buttoncell">
                    <a class="menubutton" onclick="downloadLocalDB()" id="MenuItem_ExportLocalDB">
                        <pwm:if test="showIcons"><span class="btn-icon fa fa-download"></span></pwm:if>
                        <pwm:display key="MenuItem_ExportLocalDB" bundle="Config"/>
                    </a>
                    <pwm:script>
                    <script type="application/javascript">
                        PWM_GLOBAL['startupFunctions'].push(function(){
                            makeTooltip('MenuItem_ExportLocalDB',PWM_CONFIG.showString('MenuDisplay_ExportLocalDB'));
                        });
                    </script>
                    </pwm:script>
                </td>
                <td class="buttoncell">
                    <a class="menubutton" onclick="PWM_MAIN.goto('/')" id="MenuItem_MainMenu">
                        <pwm:if test="showIcons"><span class="btn-icon fa fa-arrow-circle-left"></span></pwm:if>
                        <pwm:display key="MenuItem_MainMenu" bundle="Config"/>
                    </a>
                    <pwm:script>
                    <script type="application/javascript">
                        PWM_GLOBAL['startupFunctions'].push(function(){
                            makeTooltip('MenuItem_MainMenu',PWM_CONFIG.showString('MenuDisplay_MainMenu'));
                        });
                    </script>
                    </pwm:script>
                </td>
            </tr>
            <tr class="buttonrow">
                <td class="buttoncell">
                    <a class="menubutton" onclick="PWM_MAIN.goto('/private/admin/dashboard.jsp')" id="MenuItem_Administration">
                        <pwm:if test="showIcons"><span class="btn-icon fa fa-dashboard"></span></pwm:if>
                        <pwm:display key="Title_Admin"/>
                    </a>
                    <pwm:script>
                    <script type="application/javascript">
                        PWM_GLOBAL['startupFunctions'].push(function(){
                            makeTooltip('MenuItem_Administration',PWM_MAIN.showString('Long_Title_Admin'));
                        });
                    </script>
                    </pwm:script>
                </td>
                <% if (pwmSessionHeader.getSessionStateBean().isAuthenticated()) { %>
                <td class="buttoncell">
                    <a class="menubutton" onclick="PWM_MAIN.goto('/public/Logout')" id="MenuItem_Logout">
                        <pwm:if test="showIcons"><span class="btn-icon fa fa-sign-out"></span></pwm:if>
                        <pwm:display key="Title_Logout"/>
                    </a>
                    <pwm:script>
                    <script type="application/javascript">
                        PWM_GLOBAL['startupFunctions'].push(function(){
                            makeTooltip('MenuItem_Logout',PWM_MAIN.showString('Long_Title_Logout'));
                        });
                    </script>
                    </pwm:script>
                </td>
                <% } %>
            </tr>
        </table>
    </div>
</div>
</div>
<div class="push"></div>
</div>
<pwm:script>
<script type="text/javascript">
    PWM_GLOBAL['startupFunctions'].push(function(){
        require(["dojo/parser","dijit/TitlePane","dojo/domReady!","dojox/form/Uploader"],function(dojoParser){
            dojoParser.parse();
        });
    });

    function downloadConfig() {
        PWM_MAIN.showDialog({title:PWM_MAIN.showString("Display_PleaseWait"),text:PWM_CONFIG.showString("Warning_DownloadConfigurationInProgress"),
            loadFunction:function(){
                PWM_MAIN.goto('ConfigManager?processAction=generateXml',{addFormID:true,hideDialog:true})
            }
        });
    }

    function downloadLocalDB() {
        PWM_MAIN.showDialog({title:PWM_MAIN.showString("Display_PleaseWait"),text:PWM_CONFIG.showString("Warning_DownloadLocalDBInProgress"),
            loadFunction:function(){
                PWM_MAIN.goto('ConfigManager?processAction=exportLocalDB',{addFormID:true,hideDialog:true});
            }
        });
    }

    function downloadSupportBundle() {
        <% if (pwmApplication.getConfig().getEventLogLocalDBLevel() != PwmLogLevel.TRACE) { %>
        PWM_MAIN.showDialog({title:PWM_MAIN.showString('Title_Error'),text:PWM_CONFIG.showString("Warning_MakeSupportZipNoTrace")});
        <% } else { %>
        PWM_MAIN.showDialog({title:PWM_MAIN.showString("Display_PleaseWait"),text:PWM_CONFIG.showString("Warning_DownloadSupportZipInProgress"),
            loadFunction:function(){
                PWM_MAIN.goto('ConfigManager?processAction=generateSupportZip',{addFormID:true,hideDialog:true});
            }
        });
        <% } %>
    }

    function makeTooltip(id,text) {
        PWM_MAIN.showTooltip({
            id: id,
            showDelay: 800,
            position: ['below','above'],
            text: text,
            width: 300
        });
    }
</script>
</pwm:script>
<script type="text/javascript" src="<%=request.getContextPath()%><pwm:url url="/public/resources/js/configmanager.js"/>"></script>
<script type="text/javascript" src="<%=request.getContextPath()%><pwm:url url="/public/resources/js/admin.js"/>"></script>
<% request.setAttribute(PwmConstants.REQUEST_ATTR_SHOW_LOCALE,"false"); %>
<div><%@ include file="fragment/footer.jsp" %></div>
</body>
</html>
