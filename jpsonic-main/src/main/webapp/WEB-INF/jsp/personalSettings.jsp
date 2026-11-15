<%@ page language="java" contentType="text/html; charset=utf-8" pageEncoding="iso-8859-1" %>
<%--@elvariable id="command" type="com.tesshu.jpsonic.controller.form.PersonalSettingsCommand"--%>

<html><head>
<%@ include file="head.jsp" %>
<%@ include file="jquery.jsp" %>
<%@ page import="com.tesshu.jpsonic.domain.system.FontScheme" %>
<%@ page import="com.tesshu.jpsonic.domain.system.SpeechToTextLangScheme" %>
<script src="<c:url value='/script/utils.js'/>"></script>
<script>

function resetThemeAndLanguage() {
    $('[name="localeIndex"]').prop("selectedIndex", 0);
    $('[name="themeIndex"]').prop("selectedIndex", 0);
    $("#radio1-1").prop('checked', true);
    $("#fontFamily").val('${command.fontFamilyDefault}');
    $("#fontFamily").prop('disabled', true);
    $("#fontSize").val(${command.fontSizeDefault});
    $("#fontSizeSlider").val(${command.fontSizeDefault});
    $("#fontSizeSlider").slider({ disabled: true });
}

function speechEngineLangSelectEnabled(isEnabled) {
    $("#radio2-${command.defaultSettings.speechLangSchemeName}").prop('checked', true);
    $("#ietf").prop('disabled', !isEnabled);
    $("#radio2-DEFAULT").prop('disabled', !isEnabled);
    $("#radio2-BCP47").prop('disabled', !isEnabled);
    $("#ietf").prop('disabled', true);
}

function setSettings4DesktopPC() {
    $('#keyboardShortcutsEnabled').prop('checked', ${command.defaultSettings.keyboardShortcutsEnabled});
    $('#albumListId').val('${command.defaultSettings.defaultAlbumList.id}');
    $('#putMenuInDrawer').prop('checked', ${command.defaultSettings.putMenuInDrawer});
    $('#showIndex').prop('checked', ${command.defaultSettings.showIndex});
    $('#closeDrawer').prop('checked', ${command.defaultSettings.closeDrawer});
    $('#closePlayQueue').prop('checked', ${command.defaultSettings.closePlayQueue});
    $('#autoHidePlayQueue').prop('checked', ${command.defaultSettings.autoHidePlayQueue});
    $('#alternativeDrawer').prop('checked', ${command.defaultSettings.alternativeDrawer});
    $('#breadcrumbIndex').prop('checked', ${command.defaultSettings.breadcrumbIndex});
    $('#assignAccesskeyToNumber').prop('checked', ${command.defaultSettings.assignAccesskeyToNumber});
    $('#simpleDisplay').prop('checked', ${command.defaultSettings.simpleDisplay});
    $('#queueFollowingSongs').prop('checked', ${command.defaultSettings.queueFollowingSongs});
    $('#showCurrentSongInfo').prop('checked', ${command.defaultSettings.showCurrentSongInfo});
    speechEngineLangSelectEnabled(false);
}

function setSettings4Tablet() {
    $('#keyboardShortcutsEnabled').prop('checked', ${command.tabletSettings.keyboardShortcutsEnabled});
    $('#albumListId').val('${command.tabletSettings.defaultAlbumList.id}');
    $('#putMenuInDrawer').prop('checked', ${command.tabletSettings.putMenuInDrawer});
    $('#showIndex').prop('checked', ${command.tabletSettings.showIndex});
    $('#closeDrawer').prop('checked', ${command.tabletSettings.closeDrawer});
    $('#closePlayQueue').prop('checked', ${command.tabletSettings.closePlayQueue});
    $('#autoHidePlayQueue').prop('checked', ${command.defaultSettings.autoHidePlayQueue});
    $('#alternativeDrawer').prop('checked', ${command.tabletSettings.alternativeDrawer});
    $('#breadcrumbIndex').prop('checked', ${command.tabletSettings.breadcrumbIndex});
    $('#assignAccesskeyToNumber').prop('checked', ${command.tabletSettings.assignAccesskeyToNumber});
    $('#simpleDisplay').prop('checked', ${command.tabletSettings.simpleDisplay});
    $('#queueFollowingSongs').prop('checked', ${command.tabletSettings.queueFollowingSongs});
    $('#showCurrentSongInfo').prop('checked', ${command.tabletSettings.showCurrentSongInfo});
    speechEngineLangSelectEnabled(true);
}

function setSettings4Smartphone() {
    $('#keyboardShortcutsEnabled').prop('checked', ${command.smartphoneSettings.keyboardShortcutsEnabled});
    $('#albumListId').val('${command.smartphoneSettings.defaultAlbumList.id}');
    $('#putMenuInDrawer').prop('checked', ${command.smartphoneSettings.putMenuInDrawer});
    $('#showIndex').prop('checked', ${command.smartphoneSettings.showIndex});
    $('#closeDrawer').prop('checked', ${command.smartphoneSettings.closeDrawer});
    $('#closePlayQueue').prop('checked', ${command.smartphoneSettings.closePlayQueue});
    $('#autoHidePlayQueue').prop('checked', ${command.defaultSettings.autoHidePlayQueue});
    $('#alternativeDrawer').prop('checked', ${command.smartphoneSettings.alternativeDrawer});
    $('#breadcrumbIndex').prop('checked', ${command.smartphoneSettings.breadcrumbIndex});
    $('#assignAccesskeyToNumber').prop('checked', ${command.smartphoneSettings.assignAccesskeyToNumber});
    $('#simpleDisplay').prop('checked', ${command.smartphoneSettings.simpleDisplay});
    $('#queueFollowingSongs').prop('checked', ${command.smartphoneSettings.queueFollowingSongs});
    $('#showCurrentSongInfo').prop('checked', ${command.smartphoneSettings.showCurrentSongInfo});
    speechEngineLangSelectEnabled(true);
}

function resetDisplay() {
    $('[name="mainVisibility.trackNumberVisible"]').prop('checked', ${command.defaultSettings.mainVisibility.trackNumberVisible});
    $('[name="mainVisibility.artistVisible"]').prop('checked', ${command.defaultSettings.mainVisibility.artistVisible});
    $('[name="mainVisibility.albumVisible"]').prop('checked', ${command.defaultSettings.mainVisibility.albumVisible});
    $('[name="mainVisibility.composerVisible"]').prop('checked', ${command.defaultSettings.mainVisibility.composerVisible});
    $('[name="mainVisibility.genreVisible"]').prop('checked', ${command.defaultSettings.mainVisibility.genreVisible});
    $('[name="mainVisibility.yearVisible"]').prop('checked', ${command.defaultSettings.mainVisibility.yearVisible});
    $('[name="mainVisibility.bitRateVisible"]').prop('checked', ${command.defaultSettings.mainVisibility.bitRateVisible});
    $('[name="mainVisibility.durationVisible"]').prop('checked', ${command.defaultSettings.mainVisibility.durationVisible});
    $('[name="mainVisibility.formatVisible"]').prop('checked', ${command.defaultSettings.mainVisibility.formatVisible});
    $('[name="mainVisibility.fileSizeVisible"]').prop('checked', ${command.defaultSettings.mainVisibility.fileSizeVisible});
    $('[name="playlistVisibility.trackNumberVisible"]').prop('checked', ${command.defaultSettings.playlistVisibility.trackNumberVisible});
    $('[name="playlistVisibility.artistVisible"]').prop('checked', ${command.defaultSettings.playlistVisibility.artistVisible});
    $('[name="playlistVisibility.albumVisible"]').prop('checked', ${command.defaultSettings.playlistVisibility.albumVisible});
    $('[name="playlistVisibility.composerVisible"]').prop('checked', ${command.defaultSettings.playlistVisibility.composerVisible});
    $('[name="playlistVisibility.genreVisible"]').prop('checked', ${command.defaultSettings.playlistVisibility.genreVisible});
    $('[name="playlistVisibility.yearVisible"]').prop('checked', ${command.defaultSettings.playlistVisibility.yearVisible});
    $('[name="playlistVisibility.bitRateVisible"]').prop('checked', ${command.defaultSettings.playlistVisibility.bitRateVisible});
    $('[name="playlistVisibility.durationVisible"]').prop('checked', ${command.defaultSettings.playlistVisibility.durationVisible});
    $('[name="playlistVisibility.formatVisible"]').prop('checked', ${command.defaultSettings.playlistVisibility.formatVisible});
    $('[name="playlistVisibility.fileSizeVisible"]').prop('checked', ${command.defaultSettings.playlistVisibility.fileSizeVisible});
}

function resetAdditionalDisplay() {
    $('[name="songNotificationEnabled"]').prop('checked', ${command.defaultSettings.songNotificationEnabled});
    $('[name="voiceInputEnabled"]').prop('checked', ${command.defaultSettings.voiceInputEnabled});
    $("#ietf").val('${command.ietfDefault}');
    $("#ietf").prop('disabled', true);
    $("#radio2-DEFAULT").prop('disabled', true);
    $("#radio2-BCP47").prop('disabled', true);
    $('[name="openDetailSetting"]').prop('checked', ${command.defaultSettings.openDetailSetting});
    $('[name="openDetailStar"]').prop('checked', ${command.defaultSettings.openDetailStar});
    $('[name="openDetailIndex"]').prop('checked', ${command.defaultSettings.openDetailIndex});
    $('[name="showArtistInfoEnabled"]').prop('checked', ${command.defaultSettings.showArtistInfoEnabled});
    $('[name="forceBio2Eng"]').prop('checked', ${command.defaultSettings.forceBio2Eng});
    $('[name="showTopSongs"]').prop('checked', ${command.defaultSettings.showTopSongs});
    $('[name="showSimilar"]').prop('checked', ${command.defaultSettings.showSimilar});
    $('[name="showSibling"]').prop('checked', ${command.defaultSettings.showSibling});
    $('[name="paginationSize"]').val(${command.defaultSettings.paginationSize});
    <c:if test="${command.user.downloadRole eq true}">
        $('[name="showDownload"]').prop('checked', ${command.defaultSettings.showDownload});
    </c:if>
    <c:if test="${command.user.coverArtRole eq true}">
        $('[name="showTag"]').prop('checked', ${command.defaultSettings.showTag});
        $('[name="showChangeCoverArt"]').prop('checked', ${command.defaultSettings.showChangeCoverArt});
    </c:if>
    <c:if test="${command.user.commentRole eq true}">
        $('[name="showComment"]').prop('checked', ${command.defaultSettings.showComment});
    </c:if>
    <c:if test="${command.user.shareRole eq true}">
        $('[name="showShare"]').prop('checked', ${command.defaultSettings.showShare});
    </c:if>
    <c:if test="${command.user.commentRole eq true}">
        $('[name="showRate"]').prop('checked', ${command.defaultSettings.showRate});
    </c:if>
    $('[name="showAlbumSearch"]').prop('checked', ${command.defaultSettings.showAlbumSearch});
    $('[name="showLastPlay"]').prop('checked', ${command.defaultSettings.showLastPlay});
    $('[name="showAlbumActions"]').prop('checked', ${command.defaultSettings.showAlbumActions});
    $('[name="partyModeEnabled"]').prop('checked', ${command.defaultSettings.partyModeEnabled});
}

document.addEventListener('DOMContentLoaded', function () {
    document.getElementById('voiceInputEnabled').onchange = function (e) {
        speechEngineLangSelectEnabled(e.target.checked);
    };
    if(${command.fontScheme ne 'CUSTOM'}){
            $("#fontSizeSlider").slider({ disabled: true });
    }
    $("#radio1-1").on('change', function(e){
        $("#fontFamily").prop('disabled', true);
        $("#fontFamily").val('${command.fontFamilyDefault}');
        $("#fontSize").val(${command.fontSizeDefault});
        $("#fontSizeSlider").val(${command.fontSizeDefault});
        $("#fontSizeSlider").slider({ disabled: true });
    });
    $("#radio1-2").on('change', function(e){
        $("#fontFamily").prop('disabled', true);
        $("#fontFamily").val('${command.fontFamilyJpEmbedDefault}');
        $("#fontSize").val(${command.fontSizeJpEmbedDefault});
        $("#fontSizeSlider").val(${command.fontSizeJpEmbedDefault});
        $("#fontSizeSlider").slider({ disabled: true });
    });
    $("#radio1-3").on('change', function(e){
        $("#fontFamily").prop('disabled', false);
        $("#fontSizeSlider").slider({ disabled: false });
    });
    $("#radio2-DEFAULT").on('change', function(e){
        $("#ietf").val('${command.ietfDefault}');
        $("#ietf").prop('disabled', true);
    });
    $("#radio2-BCP47").on('change', function(e){
        $("#ietf").prop('disabled', false);
    });
    document.getElementById('fontSizeSlider').addEventListener('input', (e) => {
        $("#fontSize").val(e.target.value);
    });
}, false);

</script>

</head>

<body class="mainframe settings personalSettings">

<c:if test="${settings_reload}">
    <form:form name="reloadAll" action="index.view" method="post">
        <input name="mainView" type="hidden" value="personalSettings.view" />
    </form:form>
    <script>
        document.reloadAll.target="_top";
        document.reloadAll.submit();
    </script>
</c:if>

<c:import url="settingsHeader.jsp">
    <c:param name="cat" value="personal"/>
    <c:param name="restricted" value="${not command.user.adminRole}"/>
    <c:param name="toast" value="${command.showToast}"/>
    <c:param name="useRadio" value="${command.useRadio}"/>
    <c:param name="useDatabaseSettings" value="${command.useDatabaseSettings}"/>
    <c:param name="existsShare" value="${command.shareCount ne 0}"/>
</c:import>

<fmt:message key="common.default" var="defaultTitle"/>

<c:import url="outlineHelpSelector.jsp">
    <c:param name="targetView" value="personalSettings.view"/>
    <c:param name="showOutlineHelp" value="${command.showOutlineHelp}"/>
</c:import>

<form:form method="post" action="personalSettings.view" modelAttribute="command">

    <c:set var="isOpen" value='${command.openDetailSetting ? "open" : ""}' />
    <details ${isOpen}>
        <summary class="jpsonic"><fmt:message key="generalsettings.themeandlanguage"/></summary>

        <div class="actions">
            <ul class="controls">
                <li><a href="javascript:resetThemeAndLanguage()" title="<fmt:message key='common.reset'/>" class="control reset"><fmt:message key="common.reset"/></a></li>
            </ul>
        </div>

        <c:if test="${command.showOutlineHelp}">
            <div class="outlineHelp">
                <fmt:message key="personalsettings.themeoutline"/>
            </div>
        </c:if>

        <dl>
            <dt><fmt:message key="generalsettings.language"/></dt>
            <dd>
                <form:select path="localeIndex">
                    <form:option value="-1" label="${defaultTitle}"/>
                       <c:forEach items="${command.locales}" var="locale" varStatus="loopStatus">
                           <form:option value="${loopStatus.count - 1}" label="${locale}"/>
                       </c:forEach>
                </form:select>
            </dd>
            <dt><fmt:message key="personalsettings.theme"/></dt>
            <dd>
                <form:select path="themeIndex">
                    <form:option value="-1" label="${defaultTitle}"/>
                    <c:forEach items="${command.themes}" var="theme" varStatus="loopStatus">
                        <form:option value="${loopStatus.count - 1}" label="${theme.name}"/>
                    </c:forEach>
                </form:select>
            </dd>
            <dt><fmt:message key="personalsettings.font"/></dt>
            <dd>
                <ul class="playerSettings">
                    <c:forEach items="${FontScheme.values()}" var="scheme" varStatus="status">
                        <c:set var="fontSchemeName">
                            <fmt:message key="personalsettings.font.${fn:toLowerCase(scheme)}"/>
                        </c:set>
                        <li>
                            <form:radiobutton class="technologyRadio" id="radio1-${status.count}" path="fontScheme" value="${scheme}"
                                checked="${scheme eq command.fontScheme ? 'checked' : ''}"/>
                            <label for="radio1-${status.count}">${fontSchemeName}</label>
                            <c:if test="${scheme eq 'CUSTOM'}">
                                <form:input path="fontFamily" id="fontFamily" disabled="${command.fontScheme ne 'CUSTOM'}"/>
                            </c:if>
                            <c:import url="helpToolTip.jsp"><c:param name="topic" value="personalsettings.font.${fn:toLowerCase(scheme)}"/></c:import>
                        </li>
                    </c:forEach>
                    <li class="fontSizeSettings">
                        <fmt:message key="personalsettings.fontsize"/>
                        <input type="range" id="fontSizeSlider" min="14" max="18" value="${command.fontSize}">
                        <form:input path="fontSize" id="fontSize" readonly="true" />
                        <c:import url="helpToolTip.jsp"><c:param name="topic" value="personalsettings.fontsize"/></c:import>
                    </li>
                </ul>
            </dd>
        </dl>
    </details>

    <details open>
        <summary class="jpsonic"><fmt:message key="personalsettings.playinganddisplay"/></summary>

        <div class="actions">
            <ul class="controls">
                <li><a href="javascript:setSettings4DesktopPC()" title="<fmt:message key='personalsettings.desktoppc'/>" class="control desktop-pc"><fmt:message key="personalsettings.desktoppc"/></a></li>
                <li><a href="javascript:setSettings4Tablet()" title="<fmt:message key='personalsettings.tablet'/>" class="control tablet"><fmt:message key="personalsettings.tablet"/></a></li>
                <li><a href="javascript:setSettings4Smartphone()" title="<fmt:message key='personalsettings.smartphone'/>" class="control smartphone"><fmt:message key="personalsettings.smartphone"/></a></li>
            </ul>
        </div>

        <c:if test="${command.showOutlineHelp}">
            <div class="outlineHelp">
                <fmt:message key="personalsettings.playinganddisplayoutline"/>
            </div>
        </c:if>

        <dl>
            <dt><fmt:message key="personalsettings.pageall"/></dt>
            <dd>
                <form:checkbox path="keyboardShortcutsEnabled" id="keyboardShortcutsEnabled" />
                <label for="keyboardShortcutsEnabled"><fmt:message key="personalsettings.keyboardshortcutsenabled"/></label>
                <c:import url="helpToolTip.jsp"><c:param name="topic" value="keyboardshortcutsenabled"/></c:import>
            </dd>
            <dt><fmt:message key="personalsettings.albumlist"/></dt>
            <dd>
                <form:select path="albumListId">
                    <c:forEach items="${command.albumLists}" var="albumList" varStatus="loopStatus">
                        <c:set var="label">
                            <fmt:message key="home.${albumList.id}.title"/>
                        </c:set>
                        <form:option value="${albumList.id}" label="${label}"/>
                    </c:forEach>
                </form:select>
                <c:import url="helpToolTip.jsp"><c:param name="topic" value="albumlist"/></c:import>
            </dd>
            <dt><fmt:message key="personalsettings.pages.drawer"/></dt>
            <dd>
                <form:checkbox path="putMenuInDrawer" id="putMenuInDrawer" />
                <label for="putMenuInDrawer"><fmt:message key="personalsettings.menuindrawer"/></label>
            </dd>
            <dt><fmt:message key="personalsettings.pages.drawer"/></dt>
            <dd>
                <form:checkbox path="showIndex" id="showIndex" />
                <label for="showIndex"><fmt:message key="personalsettings.showindex"/></label>
                <c:import url="helpToolTip.jsp"><c:param name="topic" value="showindex"/></c:import>
            </dd>
            <dt><fmt:message key="personalsettings.pages.drawer"/></dt>
            <dd>
                <form:checkbox path="closeDrawer" id="closeDrawer" />
                <label for="closeDrawer"><fmt:message key="personalsettings.closedrawer"/></label>
                <c:import url="helpToolTip.jsp"><c:param name="topic" value="closedrawer"/></c:import>
            </dd>
            <dt><fmt:message key="personalsettings.pages.drawer"/></dt>
            <dd>
                <form:checkbox path="alternativeDrawer" id="alternativeDrawer" />
                <label for="alternativeDrawer"><fmt:message key="personalsettings.alternativedrawer"/></label>
                <c:import url="helpToolTip.jsp"><c:param name="topic" value="alternativedrawer"/></c:import>
            </dd>
            <dt><fmt:message key="personalsettings.pages.playqueue"/></dt>
            <dd>
                <form:checkbox path="closePlayQueue" id="closePlayQueue" />
                <label for="closePlayQueue"><fmt:message key="personalsettings.closeplayqueue"/></label>
                <c:import url="helpToolTip.jsp"><c:param name="topic" value="closeplayqueue"/></c:import>
            </dd>

            <dt><fmt:message key="personalsettings.pages.playqueue"/></dt>
            <dd>
                <%-- Param has been changed and reused. @See #622, #727, #804 --%>
                <form:checkbox path="autoHidePlayQueue" id="autoHidePlayQueue" />
                <label for="autoHidePlayQueue"><fmt:message key="personalsettings.playqueuequickopen"/></label>
            </dd>
            <dt><fmt:message key="personalsettings.pages"/> : <fmt:message key="personalsettings.pages.artist"/> / <fmt:message key="personalsettings.pages.album"/></dt>
            <dd>
                <form:checkbox path="breadcrumbIndex" id="breadcrumbIndex" />
                <label for="breadcrumbIndex"><fmt:message key="personalsettings.breadcrumbindex"/></label>
            </dd>
            <dt><fmt:message key="personalsettings.pages"/> : <fmt:message key="personalsettings.pages.drawer"/> / <fmt:message key="personalsettings.pages.home"/></dt>
            <dd>
                <form:checkbox path="assignAccesskeyToNumber" id="assignAccesskeyToNumber" />
                <label for="assignAccesskeyToNumber"><fmt:message key="personalsettings.numberaccesskey"/></label>
                <c:import url="helpToolTip.jsp"><c:param name="topic" value="numberaccesskey"/></c:import>
            </dd>
            <dt><fmt:message key="personalsettings.pages"/> : <fmt:message key="personalsettings.pages.album"/> / <fmt:message key="personalsettings.pages.playlist"/> etc</dt>
            <dd>
                <form:checkbox path="simpleDisplay" id="simpleDisplay" />
                <label for="simpleDisplay"><fmt:message key="personalsettings.simpledisplay"/></label>
            </dd>
            <dt><fmt:message key="personalsettings.pages"/> : <fmt:message key="personalsettings.pages.album"/> / <fmt:message key="personalsettings.pages.playlist"/> etc</dt>
            <dd>
                <form:checkbox path="queueFollowingSongs" id="queueFollowingSongs" />
                <label for="queueFollowingSongs"><fmt:message key="personalsettings.queuefollowingsongs"/></label>
            </dd>

            <dt></dt>
            <dd>
                <form:checkbox path="showCurrentSongInfo" id="showCurrentSongInfo" />
                <label for="showCurrentSongInfo"><fmt:message key="personalsettings.showcurrentsonginfo"/></label>
            </dd>
        </dl>
    </details>

    <details ${isOpen}>
        <summary class="jpsonic"><fmt:message key="personalsettings.display"/></summary>

        <div class="actions">
            <ul class="controls">
                <li><a href="javascript:resetDisplay()" title="<fmt:message key='common.reset'/>" class="control reset"><fmt:message key="common.reset"/></a></li>
            </ul>
        </div>

        <c:if test="${command.showOutlineHelp}">
            <div class="outlineHelp">
                <fmt:message key="personalsettings.displayoutline"/>
            </div>
        </c:if>

        <table class="tabular personalsettings-display">
            <thead>
                <tr>
                    <th><fmt:message key="personalsettings.display"/></th>
                    <th><fmt:message key="personalsettings.browse"/></th>
                    <th><fmt:message key="personalsettings.playlist"/></th>
                </tr>
            </thead>
            <tbody>
                <tr>
                    <td><fmt:message key="personalsettings.tracknumber"/></td>
                    <td><form:checkbox path="mainVisibility.trackNumberVisible" /></td>
                    <td><form:checkbox path="playlistVisibility.trackNumberVisible" /></td>
                </tr>
                <tr>
                    <td><fmt:message key="personalsettings.artist"/></td>
                    <td><form:checkbox path="mainVisibility.artistVisible" /></td>
                    <td><form:checkbox path="playlistVisibility.artistVisible" /></td>
                </tr>
                <tr>
                    <td><fmt:message key="personalsettings.album"/></td>
                    <td><form:checkbox path="mainVisibility.albumVisible" /></td>
                    <td><form:checkbox path="playlistVisibility.albumVisible" /></td>
                </tr>
                <tr>
                    <td><fmt:message key="personalsettings.composer"/></td>
                    <td><form:checkbox path="mainVisibility.composerVisible" /></td>
                    <td><form:checkbox path="playlistVisibility.composerVisible" /></td>
                </tr>
                <tr>
                    <td><fmt:message key="personalsettings.genre"/></td>
                    <td><form:checkbox path="mainVisibility.genreVisible" /></td>
                    <td><form:checkbox path="playlistVisibility.genreVisible" /></td>
                </tr>
                <tr>
                    <td><fmt:message key="personalsettings.year"/></td>
                    <td><form:checkbox path="mainVisibility.yearVisible" /></td>
                    <td><form:checkbox path="playlistVisibility.yearVisible" /></td>
                </tr>
                <tr>
                    <td><fmt:message key="personalsettings.bitrate"/></td>
                    <td><form:checkbox path="mainVisibility.bitRateVisible" /></td>
                    <td><form:checkbox path="playlistVisibility.bitRateVisible" /></td>
                </tr>
                <tr>
                    <td><fmt:message key="personalsettings.duration"/></td>
                    <td><form:checkbox path="mainVisibility.durationVisible" /></td>
                    <td><form:checkbox path="playlistVisibility.durationVisible" /></td>
                </tr>
                <tr>
                    <td><fmt:message key="personalsettings.format"/></td>
                    <td><form:checkbox path="mainVisibility.formatVisible" /></td>
                    <td><form:checkbox path="playlistVisibility.formatVisible" /></td>
                </tr>
                <tr>
                    <td><fmt:message key="personalsettings.filesize"/></td>
                    <td><form:checkbox path="mainVisibility.fileSizeVisible" /></td>
                    <td><form:checkbox path="playlistVisibility.fileSizeVisible" /></td>
                </tr>
            </tbody>
        </table>
    </details>

    <details ${isOpen}>
        <summary class="jpsonic"><fmt:message key="personalsettings.additionaldisplay"/></summary>

        <div class="actions">
            <ul class="controls">
                <li><a href="javascript:resetAdditionalDisplay()" title="<fmt:message key='common.reset'/>" class="control reset"><fmt:message key="common.reset"/></a></li>
            </ul>
        </div>

        <c:if test="${command.showOutlineHelp}">
            <div class="outlineHelp">
                <fmt:message key="personalsettings.additionaldisplayoutline"/>
            </div>
        </c:if>
        <dl>
            <dt><fmt:message key="personalsettings.notification"/></dt>
            <dd>
                <form:checkbox path="songNotificationEnabled" id="songNotificationEnabled" />
                <label for="songNotificationEnabled"><fmt:message key="personalsettings.songnotification"/></label>
            </dd>

            <dt><fmt:message key="personalsettings.speechrecognition"/></dt>
            <dd>
                <form:checkbox path="voiceInputEnabled" id="voiceInputEnabled" />
                <label for="voiceInputEnabled"><fmt:message key="personalsettings.voiceinputenabled"/></label>
                <c:import url="helpToolTip.jsp"><c:param name="topic" value="voiceinputenabled"/></c:import>
            </dd>
            <dt><fmt:message key="personalsettings.speechenginelang"/></dt>
            <dd>
                <ul class="playerSettings">
                    <c:forEach items="${SpeechToTextLangScheme.values()}" var="scheme">
                        <c:set var="speechLangSchemeName">
                            <fmt:message key="personalsettings.speechenginelang.${fn:toLowerCase(scheme)}"/>
                        </c:set>
                        <li>
                            <form:radiobutton class="technologyRadio" id="radio2-${scheme}" path="speechToTextLangScheme" value="${scheme}"
                                checked="${scheme eq command.speechToTextLangScheme ? 'checked' : ''}"
                                disabled="${!command.voiceInputEnabled}"/>
                            <label for="radio2-${scheme}">${speechLangSchemeName}</label>
                            <c:if test="${scheme eq 'DEFAULT'}">
                                  - ${command.ietfDisplayDefault}
                            </c:if>
                            <c:if test="${scheme eq 'BCP47'}">                
                                <form:input path="ietf" id="ietf" disabled="${command.speechToTextLangScheme eq 'DEFAULT'}"/>
                            </c:if>
                        </li>
                    </c:forEach>
                </ul>
            </dd>
            <dt><fmt:message key="personalsettings.summary"/></dt>
            <dd>
                <form:checkbox path="openDetailSetting" id="openDetailSetting" />
                <label for="openDetailSetting"><fmt:message key="personalsettings.summary.opensettings"/></label>
            </dd>
            <dt><fmt:message key="personalsettings.summary"/></dt>
            <dd>
                <form:checkbox path="openDetailStar" id="openDetailStar" />
                <label for="openDetailStar"><fmt:message key="personalsettings.summary.openstars"/></label>
            </dd>
            <dt><fmt:message key="personalsettings.summary"/></dt>
            <dd>
                <form:checkbox path="openDetailIndex" id="openDetailIndex" />
                <label for="openDetailIndex"><fmt:message key="personalsettings.summary.openindexes"/></label>
            </dd>
            <dt><fmt:message key="personalsettings.pages"/> : <fmt:message key="personalsettings.pages.artist"/></dt>
            <dd>
                <form:checkbox path="showArtistInfoEnabled" id="artistInfo" />
                <label for="artistInfo"><fmt:message key="personalsettings.showartistinfo"/></label>
                <div class="lastfm" title="<fmt:message key='personalsettings.lastfmapi'/>"></div>
            </dd>
            <dt><fmt:message key="personalsettings.pages"/> : <fmt:message key="personalsettings.pages.artist"/></dt>
            <dd>
                <form:checkbox path="forceBio2Eng" id="forceBio2Eng" />
                <label for="forceBio2Eng"><fmt:message key="personalsettings.forcebio2eng"/></label>
                <c:import url="helpToolTip.jsp"><c:param name="topic" value="forcebio2eng"/></c:import>
            </dd>
            <dt><fmt:message key="personalsettings.pages"/> : <fmt:message key="personalsettings.pages.artist"/></dt>
            <dd>
                <form:checkbox path="showTopSongs" id="showTopSongs" />
                <label for="showTopSongs"><fmt:message key="personalsettings.showtopsongs"/></label>
                <div class="lastfm" title="<fmt:message key='personalsettings.lastfmapi'/>"></div>
            </dd>
            <dt><fmt:message key="personalsettings.pages"/> : <fmt:message key="personalsettings.pages.artist"/></dt>
            <dd>
                <form:checkbox path="showSimilar" id="showSimilar" />
                <label for="showSimilar"><fmt:message key="personalsettings.showsimilar"/></label>
                <div class="lastfm" title="<fmt:message key='personalsettings.lastfmapi'/>"></div>
            </dd>      
            <c:if test="${command.user.commentRole}">
                <dt><fmt:message key="personalsettings.pages"/> : <fmt:message key="personalsettings.pages.artist"/> / <fmt:message key="personalsettings.pages.album"/></dt>
                <dd>
                    <form:checkbox path="showComment" id="showComment" />
                    <label for="showComment"><fmt:message key="personalsettings.showcomment"/></label>
                </dd>
            </c:if>
            <dt><fmt:message key="personalsettings.pages"/> : <fmt:message key="personalsettings.pages.album"/></dt>
            <dd>
                <form:checkbox path="showSibling" id="showSibling" />
                <label for="showSibling"><fmt:message key="personalsettings.showsibling"/></label>
            </dd>
            <dt><fmt:message key="personalsettings.pages"/> : <fmt:message key="personalsettings.pages.album"/></dt>
            <dd><form:input path="paginationSize"/>
                <fmt:message key="personalsettings.paginationsize"/>
                <c:import url="helpToolTip.jsp"><c:param name="topic" value="paginationsize"/></c:import>
            </dd>
            <c:if test="${command.user.coverArtRole}">
                <dt><fmt:message key="personalsettings.pages"/> : <fmt:message key="personalsettings.pages.album"/></dt>
                <dd>
                    <form:checkbox path="showTag" id="showTag" />
                    <label for="showTag"><fmt:message key="personalsettings.showtag"/></label>
                </dd>
                <dt><fmt:message key="personalsettings.pages"/> : <fmt:message key="personalsettings.pages.album"/></dt>
                <dd>
                    <form:checkbox path="showChangeCoverArt" id="showChangeCoverArt" />
                    <label for="showChangeCoverArt"><fmt:message key="personalsettings.showchangecoverart"/></label>
                    <div class="lastfm" title="<fmt:message key='personalsettings.lastfmapi'/>"></div>
                </dd>  
            </c:if>
            <dt><fmt:message key="personalsettings.pages"/> : <fmt:message key="personalsettings.pages.album"/></dt>
            <dd>
                <form:checkbox path="showAlbumSearch" id="showAlbumSearch" />
                <label for="showAlbumSearch"><fmt:message key="personalsettings.showalbumsearch"/></label>
            </dd>
            <dt><fmt:message key="personalsettings.pages"/> : <fmt:message key="personalsettings.pages.album"/></dt>
            <dd>
                <form:checkbox path="showLastPlay" id="showLastPlay" />
                <label for="showLastPlay"><fmt:message key="personalsettings.showlastplay"/></label>
            </dd>
            <c:if test="${command.user.commentRole}">
                <dt><fmt:message key="personalsettings.pages"/> : <fmt:message key="personalsettings.pages.album"/> / <fmt:message key="personalsettings.pages.home"/></dt>
                <dd>
                    <form:checkbox path="showRate" id="showRate" />
                    <label for="showRate"><fmt:message key="personalsettings.showrate"/></label>
                </dd>
            </c:if>
            <dt><fmt:message key="personalsettings.pages"/> : <fmt:message key="personalsettings.pages.album"/> / <fmt:message key="personalsettings.pages.playqueue"/></dt>
            <dd>
                <form:checkbox path="showAlbumActions" id="showAlbumActions" />
                <label for="showAlbumActions"><fmt:message key="personalsettings.showalbumactions"/></label>
                <c:import url="helpToolTip.jsp"><c:param name="topic" value="albumactions"/></c:import>
            </dd>
            <c:if test="${command.user.downloadRole}">
                <dt><fmt:message key="personalsettings.pages"/> : <fmt:message key="personalsettings.pages.album"/> / <fmt:message key="personalsettings.pages.playqueue"/> / <fmt:message key="personalsettings.pages.video"/></dt>
                <dd>
                    <form:checkbox path="showDownload" id="showDownload" />
                    <label for="showDownload"><fmt:message key="personalsettings.showdownload"/></label>
                </dd>
            </c:if>
            <c:if test="${command.user.shareRole}">
                <dt><fmt:message key="personalsettings.pages"/> : <fmt:message key="personalsettings.pages.album"/> / <fmt:message key="personalsettings.pages.playqueue"/> / <fmt:message key="personalsettings.pages.video"/></dt>
                <dd>
                    <form:checkbox path="showShare" id="showShare" />
                    <label for="showShare"><fmt:message key="personalsettings.showshare"/></label>
                </dd>
            </c:if>
            <c:if test="${command.usePartyMode}">
                <dt><fmt:message key="personalsettings.pages"/> : <fmt:message key="personalsettings.pages.album"/> / <fmt:message key="personalsettings.pages.playlist"/> etc</dt>
                <dd>
                    <form:checkbox path="partyModeEnabled" id="partyModeEnabled" />
                    <label for="partyModeEnabled"><fmt:message key="personalsettings.partymode"/></label>
                    <c:import url="helpToolTip.jsp"><c:param name="topic" value="partymode"/></c:import>
                </dd>
            </c:if>
        </dl>
    </details>

    <details ${isOpen}>
        <summary class="jpsonic"><fmt:message key="personalsettings.avatar.title"/></summary>
         <dl>
            <dt></dt>
            <dd>
                <form:radiobutton id="noAvatar" path="avatarId" value="-1"/>
                <label for="noAvatar"><fmt:message key="personalsettings.avatar.none"/></label>
            </dd>
            <dt></dt>
            <dd>
                <form:radiobutton id="customAvatar" path="avatarId" value="-2"/>
                <label for="customAvatar"><fmt:message key="personalsettings.avatar.custom"/>
                    <c:if test="${not empty command.customAvatar}">
                        <sub:url value="avatar.view" var="avatarUrl">
                            <sub:param name="username" value="${command.user.username}"/>
                            <sub:param name="forceCustom" value="true"/>
                        </sub:url>
                        <img src="${avatarUrl}" alt="${fn:escapeXml(command.customAvatar.name)}" width="${command.customAvatar.width}" height="${command.customAvatar.height}"/>
                    </c:if>
                </label>
            </dd>
            <dt></dt>
            <dd class="avatarContainer">
                <c:forEach items="${command.avatars}" var="avatar">
                    <c:url value="avatar.view" var="avatarUrl">
                        <c:param name="id" value="${avatar.id}"/>
                    </c:url>
                    <span class="avatar">
                        <form:radiobutton id="avatar-${avatar.id}" path="avatarId" value="${avatar.id}"/>
                        <label for="avatar-${avatar.id}"><img src="${avatarUrl}" alt="${fn:escapeXml(avatar.name)}"/></label>
                    </span>
                </c:forEach>
            </dd>
        </dl>
    </details>

    <details ${isOpen}>
        <summary class="legacy"><fmt:message key="personalsettings.musicsns"/></summary>
        <dl>
            <dt></dt>
            <dd>
                <form:checkbox path="listenBrainzEnabled" id="listenBrainz" />
                <label for="listenBrainz"><fmt:message key="personalsettings.listenbrainzenabled"/></label>
            </dd>
            <dt><fmt:message key="personalsettings.listenbrainztoken"/></dt>
            <dd><form:input path="listenBrainzToken"/></dd>
        </dl>
        <dl>
            <dt></dt>
            <dd>
                <form:checkbox path="lastFmEnabled" id="lastFm" />
                <label for="lastFm"><fmt:message key="personalsettings.lastfmenabled"/></label>
            </dd>
            <dt><fmt:message key="personalsettings.lastfmusername"/></dt>
            <dd><form:input path="lastFmUsername"/></dd>
            <dt><fmt:message key="personalsettings.lastfmpassword"/></dt>
            <dd><form:password path="lastFmPassword"/></dd>
        </dl>
    </details>

    <details ${isOpen}>
        <summary class="legacy"><fmt:message key="personalsettings.updatenotification"/></summary>
        <dl class="single">
            <dt></dt>
            <dd>
                <form:checkbox path="finalVersionNotificationEnabled" id="final" />
                <label for="final"><fmt:message key="personalsettings.finalversionnotification"/></label>
            </dd>
        </dl>
    </details>

    <div class="submits">
        <input type="submit" value="<fmt:message key='common.save'/>"/>
    </div>

</form:form>

<details ${isOpen}>
    <summary class="legacy"><fmt:message key="personalsettings.avatar.changecustom"/></summary>
    <dl class="single">
        <dt></dt>
        <dd>
            <form:form method="post" enctype="multipart/form-data" action="avatarUpload.view">
                <input type="file" id="file" name="file" mode="deleteText"/>
                <input type="submit" value="<fmt:message key='personalsettings.avatar.upload'/>" />
            </form:form>
        </dd>
    </dl>
</details>

</body></html>
