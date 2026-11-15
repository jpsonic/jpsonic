<%@ page language="java" contentType="text/html; charset=utf-8" pageEncoding="iso-8859-1"%>

<html>
<head>
<%@ include file="head.jsp"%>
<%@ include file="jquery.jsp"%>
<%@ page import="com.tesshu.jpsonic.domain.system.PreferredFormatScheme" %>
<%@ page import="com.tesshu.jpsonic.feature.transcoding.Transcodings" %>
<script>
function resetPreferredFormatSettings() {
    document.getElementsByName('preferredFormat')[0].value = 'mp3';
    $("#radio-${PreferredFormatScheme.ANNOYMOUS.name()}").prop('checked', true);
}

function resetAddTag() {
    if($("#restored${Transcodings.MP3}").prop('checked')){
        $("#addTag").prop('disabled', false);
    } else {
        $("#restored${Transcodings.MP3}").prop('checked', false);
        $("#addTag").prop('disabled', true);
    }
}

document.addEventListener('DOMContentLoaded', function () {
    $("#addTag").prop('disabled', true);    
}, false);
</script>
</head>
<body class="mainframe settings transcodingSettings">

<c:import url="settingsHeader.jsp">
    <c:param name="cat" value="transcoding" />
    <c:param name="toast" value="${settings_toast}" />
    <c:param name="useRadio" value="${model.useRadio}"/>
    <c:param name="useDatabaseSettings" value="${model.useDatabaseSettings}"/>
    <c:param name="existsShare" value="${model.shareCount ne 0}"/>
</c:import>

<c:import url="outlineHelpSelector.jsp">
    <c:param name="targetView" value="transcodingSettings.view"/>
    <c:param name="showOutlineHelp" value="${model.showOutlineHelp}"/>
</c:import>

<form method="post" action="transcodingSettings.view">
    <c:set var="isOpen" value='${model.isOpenDetailSetting ? "open" : ""}' />
    <sec:csrfInput />

    <c:if test="${not empty model.transcodings}">
        <details ${isOpen}>
            <summary><fmt:message key="transcodingsettings.registered"/></summary>

            <c:if test="${model.showOutlineHelp}">
                <div class="outlineHelp">
                    <fmt:message key="transcodingsettings.info">
                        <fmt:param value="&lt;DATADIR&gt;/transcode" />
                        <fmt:param value="${model.brand}" />
                    </fmt:message>
                <p>
                  <a href="https://airsonic.github.io/docs/transcode/" target="_blank" rel="noopener noreferrer"><fmt:message key="transcodingsettings.recommended" /></a>
                </p>
                </div>
            </c:if>

            <table class="tabular transcoding">
                <thead>
                    <tr>
                        <th><fmt:message key="transcodingsettings.name" /></th>
                        <th><fmt:message key="sharesettings.details" /></th>
                        <th><fmt:message key="common.delete" /></th>
                    </tr>
                </thead>
                <tbody>
                    <c:forEach items="${model.transcodings}" var="transcoding">
                        <tr>
                            <td>
                                <c:choose>
                                    <c:when test="${not empty Transcodings.of(transcoding.name)}" >
                                        ${fn:escapeXml(transcoding.name)}
                                        <input type="hidden" name="name[${transcoding.id}]" value="${fn:escapeXml(transcoding.name)}"/>
                                    </c:when>
                                    <c:otherwise>
                                        <input type="text" name="name[${transcoding.id}]" value="${fn:escapeXml(transcoding.name)}"/>
                                    </c:otherwise>
                                </c:choose>
                            </td>
                            <td>
                                <dl>
                                    <dt><fmt:message key="transcodingsettings.sourceformat" /> / <fmt:message key="transcodingsettings.targetformat" /></dt>
                                    <dd><input type="text" name="sourceFormats[${transcoding.id}]" value="${transcoding.sourceFormats}" /> / <input type="text" name="targetFormat[${transcoding.id}]" value="${transcoding.targetFormat}" /></dd>
                                    <dt><fmt:message key="transcodingsettings.step1" /></dt>
                                    <dd><input type="text" name="step1[${transcoding.id}]" value="${fn:escapeXml(transcoding.step1)}" /></dd>
                                    <dt><fmt:message key="transcodingsettings.step2" /></dt>
                                    <dd><input type="text" name="step2[${transcoding.id}]" value="${fn:escapeXml(transcoding.step2)}" /></dd>
                                </dl>
                            </td>
                            <td><input type="checkbox" name="delete[${transcoding.id}]" /></td>
                        </tr>
                    </c:forEach>
                </tbody>
            </table>
        </details>
    </c:if>

    <details ${isOpen}>
        <summary class="jpsonic"><fmt:message key="transcodingsettings.preferred"/></summary>

        <div class="actions">
            <ul class="controls">
                <li><a href="javascript:resetPreferredFormatSettings()" title="<fmt:message key='common.reset'/>" class="control reset"><fmt:message key="common.reset"/></a></li>
            </ul>
        </div>

        <c:if test="${model.showOutlineHelp}">
            <div class="outlineHelp">
                <fmt:message key="transcodingsettings.preferredoutline"/>
            </div>
        </c:if>

        <dl>
            <dt><fmt:message key="transcodingsettings.preferredformat"/></dt>
            <dd>
                <input type="text" name="preferredFormat" value="${model.preferredFormat}" />
                <c:import url="helpToolTip.jsp"><c:param name="topic" value="preferredformat"/></c:import>
            </dd>
            <dt>
                <fmt:message key="transcodingsettings.preferredformatscheme"/>
                <c:import url="helpToolTip.jsp"><c:param name="topic" value="preferredformatscheme"/></c:import>
            </dt>
            <dd>
                <ul class="playerSettings">
                    <c:forEach items="${PreferredFormatScheme.values()}" var="scheme">
                        <c:set var="schemeName">
                            <fmt:message key="transcodingsettings.preferredformat.${fn:toLowerCase(scheme)}"/>
                        </c:set>
                        <li>
                            <input type="radio" id="radio-${scheme.name()}" name="preferredFormatSchemeName" value="${scheme.name()}" ${scheme eq model.preferredFormatSchemeName ? 'checked' : ''}/>
                            <label for="radio-${scheme.name()}">${schemeName}</label>
                            <c:import url="helpToolTip.jsp"><c:param name="topic" value="preferredformat${fn:toLowerCase(scheme)}"/></c:import>
                        </li>
                    </c:forEach>
                </ul>
            </dd>
        </dl>
    </details>

   <details ${model.isOpenDetailSetting or empty model.transcodings ? "open" : ""}>
        <summary class="jpsonic"><fmt:message key="transcodingsettings.restore"/></summary>

        <c:if test="${empty model.transcodings}">
            <strong><fmt:message key="transcodingsettings.notranscoder"/></strong>
        </c:if>

        <c:if test="${model.showOutlineHelp}">
            <div class="outlineHelp">
                <fmt:message key="transcodingsettings.restoreoutline"/>
            </div>
        </c:if>

        <dl>
            <dt><fmt:message key="transcodingsettings.restored"/></dt>
            <dd>
                <c:forEach items="${Transcodings.values()}" var="transcoding" varStatus="loopStatus">
                     <input type="checkbox" id="restored${transcoding}" name="restoredNames" value="${transcoding.getName()}" cssClass="checkbox" onclick="resetAddTag()"/>
                    <label for="restored${transcoding}">${transcoding.getName()}</label>
                </c:forEach>
            </dd>
        </dl>
   </details>

    <details ${isOpen}>
        <summary><fmt:message key="transcodingsettings.add"/></summary>

        <table class="tabular transcoding">
            <thead>
                <tr>
                    <th><fmt:message key="transcodingsettings.name" /></th>
                    <th><fmt:message key="sharesettings.details" /></th>
                </tr>
            </thead>
            <tbody>
                <tr>
                    <td><input type="text" name="name" value="${fn:escapeXml(newTranscoding.name)}" /></td>
                    <td>
                        <dl>
                            <dt><fmt:message key="transcodingsettings.sourceformat" /> / <fmt:message key="transcodingsettings.targetformat" /></dt>
                            <dd><input type="text" name="sourceFormats" value="${newTranscoding.sourceFormats}" /> / <input name="targetFormat" value="${newTranscoding.targetFormat}" /></dd>
                            <dt><fmt:message key="transcodingsettings.step1" /></dt>
                            <dd><input type="text" name="step1" value="${fn:escapeXml(newTranscoding.step1)}" /></dd>
                            <dt><fmt:message key="transcodingsettings.step2" /></dt>
                            <dd><input type="text" name="step2" value="${fn:escapeXml(newTranscoding.step2)}" /></dd>
                        </dl>
                        <input type="checkbox" id="defaultActive" name="defaultActive" checked />
                        <label for="defaultActive"><fmt:message key="transcodingsettings.defaultactive" /></label>
                        <c:import url="helpToolTip.jsp"><c:param name="topic" value="defaultactive"/></c:import>
                    </td>
                </tr>
            </tbody>
        </table>
    </details>

    <details ${isOpen}>
        <summary><fmt:message key="advancedsettings.hlscommand" /></summary>
        <div class="hls">
            <input type="text" name="hlsCommand" value="${model.hlsCommand}" />
            <c:import url="helpToolTip.jsp">
                <c:param name="topic" value="hlscommand" />
            </c:import>
        </div>
    </details>

    <div class="submits">
        <input type="submit" value="<fmt:message key='common.save'/>">
    </div>

</form>

<c:if test="${not empty error}">
    <p><strong><fmt:message key="${error}" /></strong></p>
</c:if>

</body>
</html>
