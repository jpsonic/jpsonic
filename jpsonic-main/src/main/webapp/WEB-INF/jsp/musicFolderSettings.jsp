<%@ page language="java" contentType="text/html; charset=utf-8" pageEncoding="iso-8859-1" %>
<%--@elvariable id="command" type="com.tesshu.jpsonic.controller.form.MusicFolderSettingsCommand"--%>

<html>
<head>
<%@ include file="head.jsp" %>
<%@ include file="jquery.jsp" %>
<%@ page import="com.tesshu.jpsonic.persistence.core.entity.ScanEvent" %>

<script>
const scanning = ${command.scanning};

function init() {
    $("#newMusicFolderName").attr("placeholder", "<fmt:message key="musicfoldersettings.name"/>");
    $("#newMusicFolderPath").attr("placeholder", "<fmt:message key="musicfoldersettings.path"/>");
    <c:if test="${settings_reload}">
        window.top.reloadUpper("musicFolderSettings.view");
        window.top.reloadPlayQueue();
    </c:if>
}

document.addEventListener('DOMContentLoaded', function () {
    $("#interval").on('change', function(e) {
        if ($(this).val() == -1) {
            $("#intervalHour").prop({'disabled': true});
            $("#intervalHour option[value='3']").prop('selected', true);
        } else {
            $("#intervalHour").prop({'disabled': false});
        }
    });
}, false);

</script>
</head>
<body class="mainframe settings musicFolderSettings" onload="init()">

<c:import url="settingsHeader.jsp">
    <c:param name="cat" value="musicFolder"/>
    <c:param name="toast" value="${command.showToast}"/>
    <c:param name="useRadio" value="${command.useRadio}"/>
    <c:param name="useDatabaseSettings" value="${command.useDatabaseSettings}"/>
    <c:param name="existsShare" value="${command.shareCount ne 0}"/>
</c:import>

<c:import url="outlineHelpSelector.jsp">
    <c:param name="targetView" value="musicFolderSettings.view"/>
    <c:param name="showOutlineHelp" value="${command.showOutlineHelp}"/>
</c:import>

<form:form modelAttribute="command" action="musicFolderSettings.view" method="post">

    <c:set var="isOpen" value='${command.openDetailSetting ? "open" : ""}' />
    <details open>
        <summary class="jpsonic"><fmt:message key="musicfoldersettings.specify"/></summary>

        <c:if test="${command.showOutlineHelp}">
            <div class="outlineHelp">
                <fmt:message key="musicfoldersettings.folderoutline"/>
            </div>
        </c:if>

        <c:if test="${!empty command.musicFolders}">
            <table class="tabular musicfolder">
                <caption><fmt:message key="musicfoldersettings.registered"/></caption>
                <thead>
                    <tr>
                        <th><fmt:message key="musicfoldersettings.name"/></th>
                        <th><fmt:message key="musicfoldersettings.path"/></th>
                        <th><fmt:message key="musicfoldersettings.enabled"/></th>
                        <th><fmt:message key="common.delete"/></th>
                        <th><fmt:message key="musicfoldersettings.order"/></th>
                    </tr>
                </thead>
                <tbody>
                    <c:forEach items="${command.musicFolders}" var="folder" varStatus="loopStatus">
                        <tr>
                            <td><form:input path="musicFolders[${loopStatus.count-1}].name" disabled="${command.scanning}"/></td>
                            <td><form:input path="musicFolders[${loopStatus.count-1}].path" disabled="${command.scanning}"/></td>
                            <td><form:checkbox path="musicFolders[${loopStatus.count-1}].enabled" disabled="${command.scanning}"/></td>
                            <td><form:checkbox path="musicFolders[${loopStatus.count-1}].delete" disabled="${command.scanning}"/></td>
                            <td>
                                <c:if test="${loopStatus.count != 1 && command.musicFolders[loopStatus.count-1].enabled}">
                                    <a href="javaScript:location.href='musicFolderSettings.view?upward=${command.musicFolders[loopStatus.count-1].id}'" 
                                    	class="control up ${command.scanning ? 'disabled' : ''}"></a>
                                </c:if>
                            </td>
                        </tr>
                    </c:forEach>
                </tbody>
            </table>
        </c:if>
        <table class="tabular musicfolder">
            <caption><fmt:message key="musicfoldersettings.add"/></caption>
            <thead>
                <tr>
                    <th><fmt:message key="musicfoldersettings.name"/></th>
                    <th><fmt:message key="musicfoldersettings.path"/></th>
                    <th><fmt:message key="musicfoldersettings.enabled"/></th>
                </tr>
            </thead>
            <tbody>
                <tr>
                    <td><form:input id="newMusicFolderName" path="newMusicFolder.name" disabled="${command.scanning}"/></td>
                    <td><form:input id="newMusicFolderPath" path="newMusicFolder.path" disabled="${command.scanning}"/></td>
                    <td><form:checkbox path="newMusicFolder.enabled" cssClass="checkbox" disabled="${command.scanning}"/></td>
                </tr>
            </tbody>
        </table>
    </details>

    <details open>
        <summary class="jpsonic"><fmt:message key="musicfoldersettings.execscan"/></summary>

        <c:if test="${command.showOutlineHelp}">
            <div class="outlineHelp">
                <fmt:message key="musicfoldersettings.scanoutline"/>
            </div>
        </c:if>

        <dl>
            <dt><fmt:message key='musicfoldersettings.scannow'/><c:import url="helpToolTip.jsp"><c:param name="topic" value="scanMediaFolders"/></c:import></dt>
            <dd>
                <div>
                    <c:choose>
                        <c:when test="${command.scanning}">
                            <strong><fmt:message key="musicfoldersettings.nowscanning"/></strong>
                        </c:when>
                        <c:when test='${not command.scanning and command.ignoreFileTimestamps}'>
                            <strong><fmt:message key="musicfoldersettings.fullscannext"/></strong>
                        </c:when>
                        <c:when test='${not empty command.lastScanEventType and not (command.lastScanEventType.name() eq "SUCCESS" or command.lastScanEventType.name() eq "FINISHED")}'>
                            <strong><fmt:message key="musicfoldersettings.scaneventtype.${fn:toLowerCase(command.lastScanEventType.name())}"/></strong>
                        </c:when>
                    </c:choose>
                    <c:choose>
                        <c:when test='${command.scanning}'>
                            <c:set var="cancelDisabled" value='${command.cancel ? "disabled" : ""}' />
                            <input type="button" onClick="location.href='musicFolderSettings.view?scanCancel=true'" value="<fmt:message key='common.cancel'/>" ${cancelDisabled}/>
                        </c:when>
                        <c:otherwise>
                            <input type="button" onClick="top.onStartScanning();location.href='musicFolderSettings.view?scanNow=true'" value="<fmt:message key='musicfoldersettings.doscan'/>"/>
                        </c:otherwise>
                    </c:choose>
                </div>
            </dd>
            <dt></dt>
            <dd>
                <form:checkbox path="ignoreFileTimestamps" cssClass="checkbox" id="ignoreFileTimestamps" disabled="${command.scanning}"/>
                <c:if test='${command.ignoreFileTimestamps}'><strong></c:if>
                <form:label path="ignoreFileTimestamps"><fmt:message key="musicfoldersettings.ignorefiletimestamps"/></form:label>
                <c:if test='${command.ignoreFileTimestamps}'></strong></c:if>
                <c:import url="helpToolTip.jsp"><c:param name="topic" value="ignorefiletimestamps"/></c:import>
            </dd>
            <dt><fmt:message key="musicfoldersettings.scan"/></dt>
            <dd>
                <form:select path="interval" id="interval" disabled="${command.scanning}">
                    <fmt:message key="musicfoldersettings.interval.never" var="never"/>
                    <fmt:message key="musicfoldersettings.interval.one" var="one"/>
                    <form:option value="-1" label="${never}"/>
                    <form:option value="1" label="${one}"/>
    
                    <c:forTokens items="2 3 7 14 30 60" delims=" " var="interval">
                        <fmt:message key="musicfoldersettings.interval.many" var="many"><fmt:param value="${interval}"/></fmt:message>
                        <form:option value="${interval}" label="${many}"/>
                    </c:forTokens>
                </form:select>
                <form:select path="hour" id="intervalHour" disabled="${command.scanning}">
                    <c:forEach begin="0" end="23" var="hour">
                        <fmt:message key="musicfoldersettings.hour" var="hourLabel"><fmt:param value="${hour}"/></fmt:message>
                        <form:option value="${hour}" label="${hourLabel}"/>
                    </c:forEach>
                </form:select>
            </dd>
        </dl>
    </details>

    <details ${isOpen}>
        <summary><fmt:message key="musicfoldersettings.exclusion"/></summary>
        <dl>
            <dt><fmt:message key="musicfoldersettings.excludepattern"/></dt>
            <dd>
                <form:input path="excludePatternString" disabled="${command.scanning}"/>
                <c:import url="helpToolTip.jsp"><c:param name="topic" value="excludepattern"/></c:import>
            </dd>
            <dt></dt>
            <dd>
                <form:checkbox path="ignoreSymLinks" id="ignoreSymLinks" disabled="${command.scanning}"/>
                <form:label path="ignoreSymLinks"><fmt:message key="musicfoldersettings.ignoresymlinks"/></form:label>
            </dd>
        </dl>
    </details>

    <div class="submits">
        <c:choose>
            <c:when test='${command.scanning}'>
                <input type="submit" value="<fmt:message key='common.save'/>" disabled/>
            </c:when>
            <c:otherwise>
                <input type="submit" value="<fmt:message key='common.save'/>"/>
            </c:otherwise>
        </c:choose>
    </div>

</form:form>

</body></html>
