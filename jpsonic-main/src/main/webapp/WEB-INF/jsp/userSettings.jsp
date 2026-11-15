<%@ page language="java" contentType="text/html; charset=utf-8" pageEncoding="iso-8859-1"%>
<%--@elvariable id="command" type="com.tesshu.jpsonic.controller.form.UserSettingsCommand"--%>

<html><head>
    <%@ include file="head.jsp" %>
    <%@ include file="jquery.jsp" %>
    <%@ page import="com.tesshu.jpsonic.domain.model.TranscodingDefinition.BitRateLimit" %>
    <script src="<c:url value='/script/utils.js'/>"></script>
    <script>
        <c:if test="${settings_reload}">
            window.top.reloadUpper('userSettings.view', '${userIndex}');
        </c:if>
        function init() {
            enablePasswordChangeFields();
        }
        function enablePasswordChangeFields() {
            var changePasswordCheckbox = $("#passwordChange");
            var ldapCheckbox = $("#ldapAuthenticated");

            var changePass = Array.from(document.getElementsByClassName('changePass'));
            if (changePass != null && changePasswordCheckbox && changePasswordCheckbox.is(":checked") && (ldapCheckbox == null || !ldapCheckbox.is(":checked"))) {
                changePass.forEach(c => c.style.display = "");
            } else {
                changePass.forEach(c => c.style.display = "none");
            }

            var askPassChange = Array.from(document.getElementsByClassName('askPassChange'));
            if (askPassChange != null && changePasswordCheckbox) {
                   if (ldapCheckbox && ldapCheckbox.is(":checked")) {
                       askPassChange.forEach(a => a.style.display = "none");
                } else {
                       askPassChange.forEach(a => a.style.display = "");
                }
            }
        }
    </script>
</head>

<body class="mainframe settings userSettings" onload="init()">

<c:import url="settingsHeader.jsp">
    <c:param name="cat" value="user"/>
    <c:param name="toast" value="${command.showToast}"/>
    <c:param name="useRadio" value="${command.useRadio}"/>
    <c:param name="useDatabaseSettings" value="${command.useDatabaseSettings}"/>
    <c:param name="existsShare" value="${command.shareCount ne 0}"/>
</c:import>


<div class="topSelectorContainer">
    <dl>
        <dt><fmt:message key="usersettings.title"/></dt>
        <dd>
            <select name="username" onchange="location='userSettings.view' + selectedIndex == 0 ? 0 : '?userIndex=' + (selectedIndex - 1);">
		        <option value="">-- <fmt:message key="usersettings.newuser"/> --</option>
		        <c:forEach items="${command.users}" var="user">
		            <option ${user.username eq command.username ? "selected" : ""} value="${fn:escapeXml(user.username)}">${fn:escapeXml(user.username)}</option>
		        </c:forEach>
		    </select>
        </dd>
    </dl>
</div>

<form:form method="post" action="userSettings.view" modelAttribute="command">
    <c:choose>
        <c:when test="${command.newUser}">
            <div style="display:none"><form:input path="newUser" /></div>
        </c:when>
        <c:otherwise>
            <div style="display:none"><form:input path="username"/></div>
        </c:otherwise>
    </c:choose>
    <details open>
        <summary><fmt:message key="usersettings.usersettings"/></summary>
        <dl>
            <c:if test="${command.ldapEnabled and not command.admin}">
                <dt></dt>
                <dd>
                    <form:checkbox path="ldapAuthenticated" id="ldapAuthenticated" cssClass="checkbox" onclick="enablePasswordChangeFields()"/>
                    <label for="ldapAuthenticated"><fmt:message key="usersettings.ldap"/></label>
                    <c:import url="helpToolTip.jsp"><c:param name="topic" value="ldap"/></c:import>
                </dd>
            </c:if>
            <c:choose>
                <c:when test="${command.newUser}">
                    <dt><strong>*</strong> <fmt:message key="usersettings.username"/></dt>
                    <dd><form:input path="username"/><strong><form:errors path="username"/></strong></dd>
                    <dt><strong>*</strong> <fmt:message key="usersettings.password"/></dt>
                    <dd><form:password path="password"/><strong><form:errors path="password"/></strong></dd>
                    <dt><strong>*</strong> <fmt:message key="usersettings.confirmpassword"/></dt>
                    <dd><form:password path="confirmPassword"/></dd>
                    <dt><strong>*</strong> <fmt:message key="usersettings.email"/></dt>
                    <dd><form:input path="email"/><strong><form:errors path="email"/></strong></dd>
                </c:when>
                <c:otherwise>
                    <dt class="askPassChange" style="display:flex"></dt>
                    <dd class="askPassChange" style="display:flex">
                        <form:checkbox path="passwordChange" id="passwordChange" onclick="enablePasswordChangeFields();" cssClass="checkbox"/>
                        <label for="passwordChange"><fmt:message key="usersettings.changepassword"/></label>
                    </dd>
                    <dt class="changePass" style="display:none"><fmt:message key="usersettings.newpassword"/></dt>
                    <dd class="changePass" style="display:none"><form:password path="password" id="path"/><strong><form:errors path="password"/></strong></dd>
                    <dt class="changePass" style="display:none"><fmt:message key="usersettings.confirmpassword"/></dt>
                    <dd class="changePass" style="display:none"><form:password path="confirmPassword" id="confirmPassword"/></dd>
                    <dt><fmt:message key="usersettings.email"/></dt>
                    <dd class="notfirstLast"><form:input path="email"/><strong><form:errors path="email"/></strong></dd>
                </c:otherwise>
            </c:choose>
            <dt style="${command.currentUser ? 'display:none' : ''}"><strong><fmt:message key="usersettings.authority"/></strong></dt>
            <dd style="${command.currentUser ? 'display:none' : ''}">
                   <form:checkbox path="adminRole" id="admin" cssClass="checkbox"/>
                   <label for="admin"><fmt:message key="usersettings.admin"/></label>
                   <strong><form:errors path="adminRole"/></strong>
            </dd>
            <c:if test="${not empty command.allMusicFolders}">
                <dt><fmt:message key="usersettings.folderaccess"/></dt>
                 <dd>
                    <c:forEach items="${command.allMusicFolders}" var="musicFolder">
                        <form:checkbox path="allowedMusicFolderIds" id="musicFolder${musicFolder.id}" value="${musicFolder.id}" cssClass="checkbox"/>
                        <label for="musicFolder${musicFolder.id}">${musicFolder.name}</label>
                    </c:forEach>
                </dd>
            </c:if>
            <dt><fmt:message key="playersettings.maxbitrate"/></dt>
            <dd>
                <% pageContext.setAttribute("bitRateLimits", BitRateLimit.values()); %>
                <form:select path="bitRateLimit">
                    <c:forEach items="${bitRateLimits}" var="bitRate">
                        <form:option value="${bitRate}" label="${bitRate.toString()}"/>
                    </c:forEach>
                </form:select>
                <c:import url="helpToolTip.jsp"><c:param name="topic" value="transcode"/></c:import>
                <c:if test="${not command.transcodingSupported}">
                    <td><strong><fmt:message key="playersettings.notranscoder"/></strong></td>
                </c:if>
            </dd>
            <dt></dt>
            <dd>
                <form:checkbox path="settingsRole" id="settings" cssClass="checkbox"/>
                <label for="settings"><fmt:message key="usersettings.settings"/></label>
            </dd>
            <dt></dt>
            <dd>
                <form:checkbox path="streamRole" id="stream" cssClass="checkbox"/>
                <label for="stream"><fmt:message key="usersettings.stream"/></label>
            </dd>
            <dt></dt>
            <dd>
                <form:checkbox path="downloadRole" id="download" cssClass="checkbox"/>
                <label for="download"><fmt:message key="usersettings.download"/></label>
                <c:import url="helpToolTip.jsp"><c:param name="topic" value="usersettingsrequireuserenable"/></c:import>
            </dd>
            <dt></dt>
            <dd>
                <form:checkbox path="uploadRole" id="upload" cssClass="checkbox"/>
                <label for="upload"><fmt:message key="usersettings.upload"/></label>
                <c:import url="helpToolTip.jsp"><c:param name="topic" value="usersettingsbuttonondrawer"/></c:import>
            </dd>
            <dt></dt>
            <dd>
                <form:checkbox path="shareRole" id="share" cssClass="checkbox"/>
                <label for="share"><fmt:message key="usersettings.share"/></label>
                <c:import url="helpToolTip.jsp"><c:param name="topic" value="usersettingsrequireuserenable"/></c:import>
            </dd>
            <dt></dt>
            <dd>
                <form:checkbox path="coverArtRole" id="coverArt" cssClass="checkbox"/>
                <label for="coverArt"><fmt:message key="usersettings.coverart"/></label>
                <c:import url="helpToolTip.jsp"><c:param name="topic" value="usersettingsrequireuserenable"/></c:import>
            </dd>
            <dt></dt>
            <dd>
                <form:checkbox path="commentRole" id="comment" cssClass="checkbox"/>
                <label for="comment"><fmt:message key="usersettings.comment"/></label>
                <c:import url="helpToolTip.jsp"><c:param name="topic" value="usersettingsrequireuserenable"/></c:import>
            </dd>
            <dt></dt>
            <dd>
                <form:checkbox path="podcastRole" id="podcast" cssClass="checkbox"/>
                <label for="podcast"><fmt:message key="usersettings.podcast"/></label>
                <c:import url="helpToolTip.jsp"><c:param name="topic" value="usersettingspodcastenable"/></c:import>
            </dd>
            <dt></dt>
            <dd>
                <form:checkbox path="nowPlayingAllowed" id="nowPlayingAllowed" cssClass="checkbox"/>
                <label for="nowPlayingAllowed"><fmt:message key="usersettings.nowplayingallowed"/></label>
                <c:import url="helpToolTip.jsp"><c:param name="topic" value="usersettingsnowplayingallowed"/></c:import>
            </dd>
        </dl>

    </details>

    <c:if test="${not command.newUser and not command.admin and not command.currentUser}">
        <details>
            <summary><fmt:message key="usersettings.deleteuser"/></summary>
            <p>
            <form:checkbox path="deleteUser" id="delete" cssClass="checkbox"/>
            <label for="delete"><fmt:message key="usersettings.delete"/></label>
            <strong><form:errors path="deleteUser"/></strong>
            </p>
        </details>
    </c:if>

    <div class="submits">
        <input type="submit" value="<fmt:message key='common.save'/>">
    </div>

</form:form>

</body></html>
