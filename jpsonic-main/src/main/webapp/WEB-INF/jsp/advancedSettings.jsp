<%@ page language="java" contentType="text/html; charset=utf-8" pageEncoding="iso-8859-1" %>
<%--@elvariable id="command" type="com.tesshu.jpsonic.controller.form.AdvancedSettingsCommand"--%>

<html><head>
<%@ include file="head.jsp" %>
<%@ include file="jquery.jsp" %>
<%@ page import="com.tesshu.jpsonic.domain.system.IndexScheme" %>
<%@ page import="com.tesshu.jpsonic.feature.auth.rememberme.RMSKeys" %>
<%@ page import="com.tesshu.jpsonic.feature.auth.rememberme.KeyRotationType" %>
<%@ page import="com.tesshu.jpsonic.feature.auth.rememberme.KeyRotationPeriod" %>
<%@ page import="com.tesshu.jpsonic.feature.auth.rememberme.TokenValidityPeriod" %>
<script src="<c:url value='/script/utils.js'/>"></script>
<script>
const scanning = ${command.scanning};


function resetRememberMe() {
    $("#rememberMeEnable").prop('checked', true);
    $("#rememberMeKeyRotationType").prop("selectedIndex", 1);
    $("#rememberMeKeyRotationPeriod").prop("selectedIndex", 2);
    $("#rememberMeTokenValidityPeriod").prop("selectedIndex", 1);
    $("#slidingExpirationEnabled").prop('checked', true);
    onRememberMeEnabled();
}

function resetBandwidth() {
    $('[name="downloadLimit"]').val(0);
    $('[name="uploadLimit"]').val(0);
    $('[name="bufferSize"]').prop("selectedIndex", 3);
    $("#forceCompatibilityMode").prop('checked', false);
}

function resetScanLog() {
    if(scanning) {
        return;
    }
    $("#usescanlog").prop('checked', false);
    $("#scanlogretention").prop('disabled', true);
    $("#scanlogretention").val(-1);
    $("#usescanevents").prop({'disabled': true, 'checked': false});
    $("#measurememory").prop({'disabled': true, 'checked': false});
}

function onRememberMeEnabled() {
    if ($("#rememberMeEnable").prop("checked")) {
        $("#rememberMeKeyRotationType").prop('disabled', false);
        $("#rememberMeKeyRotationPeriod").prop('disabled', false);
        $("#rememberMeTokenValidityPeriod").prop('disabled', false);
        $("#slidingExpirationEnabled").prop('disabled', false);
        $("#rotateNow").prop('disabled', false);
    } else {
        $("#rememberMeKeyRotationType").prop('disabled', true);
        $("#rememberMeKeyRotationPeriod").prop('disabled', true);
        $("#rememberMeTokenValidityPeriod").prop('disabled', true);
        $("#slidingExpirationEnabled").prop('disabled', true);
        $("#rotateNow").prop('disabled', true);
    }
}

function rememberMeKeyRotationTypeChanged() {
    if (!$("#rememberMeEnable").prop("checked")) {
        return;
    }
    var value = $("#rememberMeKeyRotationType").val();
    $("#rememberMeKeyRotationPeriod").prop('disabled', value !== '${KeyRotationType.PERIOD.name()}');
}

function onUseScanLogStateChanged() {
    if(scanning) {
        return;
    }
    if($("#usescanlog").prop("checked")){
        $("#scanlogretention").prop('disabled', false);
        $("#usescanevents").prop({'disabled': false});
        $("#measurememory").prop({'disabled': false});
    } else {
        $("#scanlogretention").prop('disabled', true);
        $("#scanlogretention").val(-1);
        $("#usescanevents").prop({'disabled': true, 'checked': false});
        $("#measurememory").prop({'disabled': true, 'checked': false});
    }
}

document.addEventListener('DOMContentLoaded', function () {

    onUseScanLogStateChanged();
    $("#usescanlog").on('change', function(e){
        onUseScanLogStateChanged();
    });

    onRememberMeEnabled();
    $("#rememberMeEnable").on('change', function(e){
        onRememberMeEnabled();
    });

    rememberMeKeyRotationTypeChanged();
    $("#rememberMeKeyRotationType").on('change', function(e){
        rememberMeKeyRotationTypeChanged();
    });

    $("#radio1-1").on('change', function(e){
        $("#readGreekInJapanese").prop({'disabled': false, 'checked': true});
        $("#forceInternalValueInsteadOfTags").prop({'disabled': true, 'checked': false});
    });
    $("#radio1-2").on('change', function(e){
        $("#readGreekInJapanese").prop({'disabled': true, 'checked': false});
        $("#forceInternalValueInsteadOfTags").prop({'disabled': false, 'checked': true});
    });
    $("#radio1-3").on('change', function(e){
        $("#readGreekInJapanese").prop({'disabled': true, 'checked': false});
        $("#forceInternalValueInsteadOfTags").prop({'disabled': true, 'checked': false});
    });
}, false);

function postRotate() {
    document.getElementById('rotateForm').submit();
}

</script>
</head>

<body class="mainframe settings advancedSettings">

<c:import url="settingsHeader.jsp">
    <c:param name="cat" value="advanced"/>
    <c:param name="toast" value="${settings_toast}"/>
    <c:param name="useRadio" value="${command.useRadio}"/>
    <c:param name="useDatabaseSettings" value="${command.useDatabaseSettings}"/>
    <c:param name="existsShare" value="${command.shareCount ne 0}"/>
</c:import>

<c:import url="outlineHelpSelector.jsp">
    <c:param name="targetView" value="advancedSettings.view"/>
    <c:param name="showOutlineHelp" value="${command.showOutlineHelp}"/>
</c:import>

<form:form method="post" action="advancedSettings.view" modelAttribute="command">

    <c:set var="isOpen" value='${command.openDetailSetting ? "open" : ""}' />

    <details ${isOpen}>

        <div class="actions">
            <ul class="controls">
                <li><a href="javascript:resetRememberMe()" title="<fmt:message key='common.reset'/>" class="control reset"><fmt:message key="common.reset"/></a></li>
            </ul>
        </div>

        <summary class="jpsonic">Remember Me</summary>
        <dl>
            <dt>
                <fmt:message key="advancedsettings.rememberme.enable"/>
                <c:import url="helpToolTip.jsp"><c:param name="topic" value="rememberme-enable"/></c:import>
            </dt>
            <dd>
                <form:checkbox path="rememberMeEnable" id="rememberMeEnable"/>
                <label for="rememberMeEnable"><fmt:message key="advancedsettings.rememberme.on"/></label>
            </dd>
            <dt>
                <fmt:message key="advancedsettings.rememberme.rotation-type"/>
                <c:import url="helpToolTip.jsp"><c:param name="topic" value="rememberme-rotation-type"/></c:import>
            </dt>
            <dd>
                <form:select path="rememberMeKeyRotationType" id="rememberMeKeyRotationType">
                    <c:forEach items="${KeyRotationType.values()}" var="keyRotationType">
                        <c:set var="keyRotationTypeViewName">
                            <fmt:message key="advancedsettings.rememberme.rotation-type.${fn:toLowerCase(keyRotationType)}"/>
                        </c:set>
                        <form:option value="${keyRotationType}" label="${keyRotationTypeViewName}"/>
                    </c:forEach>
                </form:select>
            </dd>
            <dt>
                <fmt:message key="advancedsettings.rememberme.rotation-period"/>
                <c:import url="helpToolTip.jsp"><c:param name="topic" value="rememberme-rotation-period"/></c:import>
            </dt>
            <dd>
                <form:select path="rememberMeKeyRotationPeriod" id="rememberMeKeyRotationPeriod">
                    <c:forEach items="${KeyRotationPeriod.values()}" var="keyRotationPeriod">
                        <c:set var="keyRotationPeriodViewName">
                            <fmt:message key="advancedsettings.rememberme.rotation-period.${fn:toLowerCase(keyRotationPeriod)}"/>
                        </c:set>
                        <form:option value="${keyRotationPeriod}" label="${keyRotationPeriodViewName}"/>
                    </c:forEach>
                </form:select>
            </dd>
            <dt>
                <fmt:message key="advancedsettings.rememberme.token-validity-period"/>
                <c:import url="helpToolTip.jsp"><c:param name="topic" value="rememberme-token-validity-period"/></c:import>
            </dt>
            <dd>
                <form:select path="rememberMeTokenValidityPeriod" id="rememberMeTokenValidityPeriod">
                    <c:forEach items="${TokenValidityPeriod.values()}" var="tokenValidityPeriod">
                        <c:set var="tokenValidityPeriodViewName">
                            <fmt:message key="advancedsettings.rememberme.token-validity-period.${fn:toLowerCase(tokenValidityPeriod)}"/>
                        </c:set>
                        <form:option value="${tokenValidityPeriod}" label="${tokenValidityPeriodViewName}"/>
                    </c:forEach>
                </form:select>
            </dd>
            <dt>
                <fmt:message key="advancedsettings.rememberme.sliding-expiration-enable"/>
                <c:import url="helpToolTip.jsp"><c:param name="topic" value="rememberme-sliding-expiration-enable"/></c:import>
            </dt>
            <dd>
                <form:checkbox path="slidingExpirationEnabled" id="slidingExpirationEnabled"/>
                <label for="slidingExpirationEnabled"><fmt:message key="advancedsettings.rememberme.on"/></label>
            </dd>
            <dt><fmt:message key='advancedsettings.rememberme.key-lastupdate'/></dt>
            <dd>${command.rememberMeLastUpdate}
            </dd>
            <dt>
                <fmt:message key='advancedsettings.rememberme.key-rotate'/>
                <c:import url="helpToolTip.jsp"><c:param name="topic" value="rememberme-exec-rotation"/></c:import>
            </dt>
            <dd>
                <div>
                    <input id="rotateNow" type="button" onClick="postRotate()" value="<fmt:message key='advancedsettings.rememberme.exec-key-rotate'/>"/>
                </div>
            </dd>
        </dl>
    </details>

    <details ${isOpen}>
        <summary class="jpsonic"><fmt:message key="advancedsettings.bandwidth"/></summary>

        <div class="actions">
            <ul class="controls">
                <li><a href="javascript:resetBandwidth()" title="<fmt:message key='common.reset'/>" class="control reset"><fmt:message key="common.reset"/></a></li>
            </ul>
        </div>

        <dl>
            <dt><fmt:message key="advancedsettings.downloadlimit"/></dt>
            <dd>
                <form:input path="downloadLimit"/>
                <c:import url="helpToolTip.jsp"><c:param name="topic" value="downloadlimit"/></c:import>
            </dd>
            <dt><fmt:message key="advancedsettings.uploadlimit"/></dt>
            <dd>
                <form:input path="uploadLimit"/>
                <c:import url="helpToolTip.jsp"><c:param name="topic" value="uploadlimit"/></c:import>
            </dd>
            <dt><fmt:message key="advancedsettings.buffersize"/></dt>
            <dd>
                <form:select path="bufferSize">
                    <form:option value="4096" label="4Kb: tcp_rmem#min"/>
                    <form:option value="8192" label="8Kb"/>
                    <form:option value="16384" label="16KB"/>
                    <form:option value="32768" label="32KB: Default"/>
                    <form:option value="65536" label="64KB"/>
                    <form:option value="87380" label="87,380: tcp_rmem#mid"/>
                    <form:option value="1048576" label="1Mb"/>
                    <form:option value="6291456" label="6Mb: tcp_rmem#max"/>
                </form:select>
                <c:import url="helpToolTip.jsp"><c:param name="topic" value="buffersize"/></c:import>
            </dd>
            <dt></dt>
            <dd>
                <form:checkbox path="forceCompatibilityMode" id="forceCompatibilityMode"/>
                <label for="forceCompatibilityMode"><fmt:message key="advancedsettings.forcecompatibilitymode"/></label>
                <c:import url="helpToolTip.jsp"><c:param name="topic" value="forcecompatibilitymode"/></c:import>
            </dd>
        </dl>
    </details>

    <details ${isOpen}>
        <summary><fmt:message key="advancedsettings.email"/></summary>
        <dl>
            <dt><fmt:message key="advancedsettings.smtpFrom"/></dt>
            <dd>
                <form:input path="smtpFrom"/>
                <c:import url="helpToolTip.jsp"><c:param name="topic" value="smtpFrom"/></c:import>
            </dd>
            <dt><fmt:message key="advancedsettings.smtpServer"/></dt>
            <dd>
                <form:input path="smtpServer"/>
                <c:import url="helpToolTip.jsp"><c:param name="topic" value="smtpServer"/></c:import>
            </dd>
            <dt><fmt:message key="advancedsettings.smtpPort"/></dt>
            <dd>
                <form:input path="smtpPort"/>
                <c:import url="helpToolTip.jsp"><c:param name="topic" value="smtpPort"/></c:import>
            </dd>
            <dt><fmt:message key="advancedsettings.smtpEncryption"/></dt>
            <dd>
                <form:select path="smtpEncryption">
                    <fmt:message key="advancedsettings.smtpEncryption.none" var="none"/>
                    <fmt:message key="advancedsettings.smtpEncryption.starttls" var="starttls"/>
                    <fmt:message key="advancedsettings.smtpEncryption.ssl" var="ssl"/>
                    <form:option value="None" label="${none}"/>
                    <form:option value="STARTTLS" label="${starttls}"/>
                    <form:option value="SSL/TLS" label="${ssl}"/>
                </form:select>
                <c:import url="helpToolTip.jsp"><c:param name="topic" value="smtpEncryption"/></c:import>
            </dd>
            <dt><fmt:message key="advancedsettings.smtpUser"/></dt>
            <dd>
                <form:input path="smtpUser"/>
            </dd>
            <dt><fmt:message key="advancedsettings.smtpPassword"/></dt>
            <dd>
                <form:password path="smtpPassword"/>
                <c:import url="helpToolTip.jsp"><c:param name="topic" value="smtpCredentials"/></c:import>
            </dd>
        </dl>
    </details>

    <details ${isOpen}>
        <summary><fmt:message key="advancedsettings.ldap"/></summary>
        <p><strong><fmt:message key="advancedsettings.ldapRequiresRestart"/></strong></p>
        <dl>
            <dt></dt>
            <dd>
                <form:checkbox path="ldapEnabled" id="ldap"/>
                <label for="ldap"><fmt:message key="advancedsettings.ldapenabled"/></label>
                <c:import url="helpToolTip.jsp"><c:param name="topic" value="ldap"/></c:import>
            </dd>
            <dt><fmt:message key="advancedsettings.ldapurl" /></dt>
            <dd>
                <form:input path="ldapUrl"/>
                <c:import url="helpToolTip.jsp"><c:param name="topic" value="ldapurl" /></c:import>
            </dd>
            <dt><fmt:message key="advancedsettings.ldapsearchfilter" /></dt>
            <dd>
                <form:input path="ldapSearchFilter"/>
                <c:import url="helpToolTip.jsp"><c:param name="topic" value="ldapsearchfilter" /></c:import>
            </dd>
            <dt><fmt:message key="advancedsettings.ldapmanagerdn" /></dt>
            <dd><form:input path="ldapManagerDn"/></dd>
            <dt><fmt:message key="advancedsettings.ldapmanagerpassword" /></dt>
            <dd>
                <form:password path="ldapManagerPassword"/>
                <c:import url="helpToolTip.jsp"><c:param name="topic" value="ldapmanagerdn" /></c:import>
            </dd>
            <dt></dt>
            <dd>
                <form:checkbox path="ldapAutoShadowing" id="ldapAutoShadowing" cssClass="checkbox" />
                <label for="ldapAutoShadowing"><fmt:message key="advancedsettings.ldapautoshadowing">
                    <fmt:param value="${command.brand}" /></fmt:message>
                </label>
                <c:import url="helpToolTip.jsp"><c:param name="topic" value="ldapautoshadowing" /></c:import>
            </dd>
        </dl>
    </details>

    <details ${isOpen}>
        <summary><fmt:message key="advancedsettings.accountrecovery"/></summary>
        <dl>
            <dt></dt>
            <dd>
                <form:checkbox path="captchaEnabled" id="enablecaptcha"/>
                <label for="enablecaptcha"><fmt:message key="advancedsettings.enableCaptcha"/></label>
                <c:import url="helpToolTip.jsp"><c:param name="topic" value="captcha"/></c:import>
            </dd>
            <dt><fmt:message key="advancedsettings.recaptchaSiteKey"/></dt>
            <dd>
                <form:input path="recaptchaSiteKey"/>
                <c:import url="helpToolTip.jsp"><c:param name="topic" value="recaptchaSiteKey"/></c:import>
            </dd>
            <dt><fmt:message key="advancedsettings.recaptchaSecretKey"/></dt>
            <dd>
                <form:input path="recaptchaSecretKey"/>
                <c:import url="helpToolTip.jsp"><c:param name="topic" value="recaptchaSecretKey"/></c:import>
            </dd>
        </dl>
    </details>

    <details ${isOpen}>

        <div class="actions">
            <ul class="controls">
                <li><a href="javascript:resetScanLog()" title="<fmt:message key='common.reset'/>" class="control reset"><fmt:message key="common.reset"/></a></li>
            </ul>
        </div>

        <c:if test="${command.showOutlineHelp}">
            <div class="outlineHelp">
                <fmt:message key="helppopup.scanlogoutline"/>
            </div>
        </c:if>

        <summary class="jpsonic"><fmt:message key="advancedsettings.scanlog"/></summary>
        <dl>
            <dt></dt>
            <dd>
                <form:checkbox path="useScanLog" id="usescanlog" disabled="${command.scanning}"/>
                <label for="usescanlog"><fmt:message key="advancedsettings.usescanlog"/></label>
                <c:import url="helpToolTip.jsp"><c:param name="topic" value="usescanlog"/></c:import>
            </dd>
            <dt><fmt:message key="advancedsettings.scanlogretention"/></dt>
            <dd>
                <form:select path="scanLogRetention" id="scanlogretention" disabled="${command.scanning}">
                    <fmt:message key="advancedsettings.retentionmin" var="latest"/>
                    <fmt:message key="advancedsettings.retention" var="week">
                        <fmt:param value="7" />
                    </fmt:message>
                    <fmt:message key="advancedsettings.retention" var="month">
                        <fmt:param value="30" />
                    </fmt:message>
                    <fmt:message key="advancedsettings.retention" var="halfYear">
                        <fmt:param value="180" />
                    </fmt:message>
                    <form:option value="-1" label="${latest}"/>
                    <form:option value="7" label="${week}"/>
                    <form:option value="30" label="${month}"/>
                    <form:option value="180" label="${halfYear}"/>
                </form:select>
                <c:import url="helpToolTip.jsp"><c:param name="topic" value="scanlogretention"/></c:import>
            </dd>
            <dt></dt>
            <dd>
                <form:checkbox path="useScanEvents" id="usescanevents" disabled="${command.scanning}"/>
                <label for="usescanevents"><fmt:message key="advancedsettings.usescanevents"/></label>
                <c:import url="helpToolTip.jsp"><c:param name="topic" value="usescanevents"/></c:import>
            </dd>
            <dt></dt>
            <dd>
                <form:checkbox path="measureMemory" id="measurememory" disabled="${command.scanning}"/>
                <label for="measurememory"><fmt:message key="advancedsettings.measurememory"/></label>
                <c:import url="helpToolTip.jsp"><c:param name="topic" value="measurememory"/></c:import>
            </dd>
        </dl>
    </details>

    <details ${isOpen}>

        <c:if test="${command.showOutlineHelp}">
            <div class="outlineHelp">
                <fmt:message key="helppopup.indexschemeoutline"/>
            </div>
        </c:if>

        <summary class="jpsonic"><strong>Danger Zone</strong></summary>
        <dl>
            <dt><strong><fmt:message key="advancedsettings.indexscheme"/></strong></dt>
            <dd class="scheme">
                <ul class="playerSettings">
                    <c:forEach items="${IndexScheme.values()}" var="indexScheme" varStatus="status">
                        <li>
                            <form:radiobutton class="technologyRadio" id="radio1-${status.count}" path="indexScheme" value="${indexScheme}"
                                checked="${indexScheme eq command.indexScheme.name() ? 'checked' : ''}" disabled="${command.scanning}"/>
                            <label for="radio1-${status.count}"><fmt:message key="advancedsettings.indexscheme.${fn:toLowerCase(indexScheme)}"/></label>
                            <c:import url="helpToolTip.jsp"><c:param name="topic" value="${fn:toLowerCase(indexScheme)}" /></c:import>
                            <c:if test='${"ROMANIZED_JAPANESE" eq indexScheme}'>
                                <li class="subItem">
                                    <form:checkbox path="forceInternalValueInsteadOfTags" id="forceInternalValueInsteadOfTags" cssClass="checkbox" disabled="${command.scanning}"/>
                                    <label for="forceInternalValueInsteadOfTags"><fmt:message key="advancedsettings.forceinternalvalueinsteadoftags"/></label>
                                    <c:import url="helpToolTip.jsp"><c:param name="topic" value="forceinternalvalueinsteadoftags"/></c:import>
                                </li>
                            </c:if>                      
                        </li>
                    </c:forEach>
                </ul>
            </dd>
            <dt></dt>
            <dd>
                <form:checkbox path="sortAlphanum" id="sortAlphanum" disabled="${command.scanning}"/>
                <label for="sortAlphanum"><fmt:message key="generalsettings.sortalphanum"/></label>
                <c:import url="helpToolTip.jsp"><c:param name="topic" value="sortalphanum"/></c:import>
            </dd>
            <dt></dt>
            <dd>
                <form:checkbox path="sortStrict" id="sortStrict" disabled="${command.scanning}"/>
                <label for="sortStrict"><fmt:message key="generalsettings.sortstrict"/></label>
                <c:import url="helpToolTip.jsp"><c:param name="topic" value="sortstrict"/></c:import>
            </dd>
        </dl>
    </details>

    <div class="submits">
        <input type="submit" value="<fmt:message key='common.save'/>">
    </div>

</form:form>

<form:form id="rotateForm" action="advancedSettings/rotate" method="post" style="display:none;" />

<c:if test="${settings_reload}">
    <script>
        window.top.reloadPlayQueue();
        window.top.reloadUpper("advancedSettings.view");
    </script>
</c:if>

</body></html>
