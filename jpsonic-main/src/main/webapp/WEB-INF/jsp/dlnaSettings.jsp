<%@ page language="java" contentType="text/html; charset=utf-8" pageEncoding="iso-8859-1" %>
<%--
  ~ This file is part of Airsonic.
  ~
  ~ Airsonic is free software: you can redistribute it and/or modify
  ~ it under the terms of the GNU General Public License as published by
  ~ the Free Software Foundation, either version 3 of the License, or
  ~ (at your option) any later version.
  ~
  ~ Airsonic is distributed in the hope that it will be useful,
  ~ but WITHOUT ANY WARRANTY; without even the implied warranty of
  ~ MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  ~ GNU General Public License for more details.
  ~
  ~ You should have received a copy of the GNU General Public License
  ~ along with Airsonic.  If not, see <http://www.gnu.org/licenses/>.
  ~
  ~ Copyright 2013 (C) Sindre Mehus
  --%>

<html><head>
<%@ include file="head.jsp" %>
<%@ include file="jquery.jsp" %>
<%@ page import="com.tesshu.jpsonic.domain.model.TranscodingDefinition.BitRateLimit" %>
<%@ page import="com.tesshu.jpsonic.domain.system.MenuItemId" %>
<%@ page import="com.tesshu.jpsonic.infrastructure.search.UPnPSearchMethod" %>
<%@ page import="com.tesshu.jpsonic.feature.crypt.upnp.KeyRotationType" %>
<%@ page import="com.tesshu.jpsonic.feature.crypt.upnp.KeyRotationPeriod" %>
<%@ page import="com.tesshu.jpsonic.infrastructure.menu.MenuItemManager.ResetMode" %>

<script src="<c:url value='/script/utils.js'/>"></script>
<script>

    function checkBitrateAvailability() {
        var c = 0;
        Array.from(document.getElementsByName('activeTranscodingIds')).forEach(a => {if (a.checked) {c++}});
        $('[name="bitRateLimit"]').prop("disabled", c == 0);
    }

    function resetBasicSettings() {
        Array.from(document.getElementsByName('allowedMusicFolderIds')).forEach(a => a.checked = true);
        $('[name="bitRateLimit"]').prop("selectedIndex", 4);
        Array.from(document.getElementsByName('activeTranscodingIds')).forEach(a => a.checked = false);
        checkBitrateAvailability();
    }

    function resetMiscellaneous() {
        $("#dlnaEnabledFilteredIp").prop('checked', true);
        $("#dlnaFilteredIp").prop('disabled', false);
        $("#dlnaFilteredIp").val('172.17.16.1');
        $("#dlnaKeyRotationType").prop("selectedIndex", 2);
        $("#dlnaKeyRotationPeriod").prop("selectedIndex", 2);
        dlnaKeyKeyRotationTypeChanged();
    }

    function dlnaKeyKeyRotationTypeChanged() {
        var value = $("#dlnaKeyRotationType").val();
        $("#dlnaKeyRotationPeriod").prop('disabled', value !== '${KeyRotationType.PERIOD.name()}');
    }

    $(document).ready(function(){
        Array.from(document.getElementsByName('activeTranscodingIds')).forEach(a => a.onclick = checkBitrateAvailability);
        checkBitrateAvailability();
    });

    document.addEventListener('DOMContentLoaded', function () {
        document.getElementById('dlnaEnabledFilteredIp').onchange = function (e) {
            $("#dlnaFilteredIp").prop('disabled', !e.target.checked);
        };
        $("#dlnaFilteredIp").prop('disabled', !${command.dlnaEnabledFilteredIp});

        dlnaKeyKeyRotationTypeChanged();
        $("#dlnaKeyRotationType").on('change', function(e){
            dlnaKeyKeyRotationTypeChanged();
        });

    }, false);

    function postRotate() {
        document.getElementById('rotateForm').submit();
    }

</script>
</head>

<body class="mainframe settings dlnaSettings">

<c:import url="settingsHeader.jsp">
    <c:param name="cat" value="dlna"/>
    <c:param name="toast" value='${settings_toast}'/>
    <c:param name="useRadio" value="${command.useRadio}"/>
    <c:param name="useDatabaseSettings" value="${command.useDatabaseSettings}"/>
    <c:param name="existsShare" value="${command.shareCount ne 0}"/>
</c:import>

<c:import url="outlineHelpSelector.jsp">
    <c:param name="targetView" value="dlnaSettings.view"/>
    <c:param name="showOutlineHelp" value="${command.showOutlineHelp}"/>
</c:import>

<form:form method="post" action="dlnaSettings.view" modelAttribute="command">

    <c:set var="isOpen" value='${command.openDetailSetting ? "open" : ""}' />
    <details open>

        <summary class="jpsonic"><fmt:message key="dlnasettings.basic"/></summary>

        <c:if test="${command.showOutlineHelp}">
            <div class="outlineHelp"><fmt:message key="dlnasettings.basicoutline"/></div>
        </c:if>

        <div class="actions">
            <ul class="controls">
                <li><a href="javascript:resetBasicSettings()" title="<fmt:message key='common.reset'/>" class="control reset"><fmt:message key="common.reset"/></a></li>
            </ul>
        </div>

        <dl>
            <dt></dt>
            <dd>
                <form:checkbox path="dlnaEnabled" id="dlnaEnabled"/>
                <label for="dlnaEnabled"><fmt:message key="dlnasettings.enabled"/></label>
                <c:import url="helpToolTip.jsp"><c:param name="topic" value="dlnaenable"/></c:import>
                <c:if test="${command.wsl}">
                    <strong><fmt:message key="dlnasettings.wsl"/></strong>
                </c:if>
            </dd>
            <dt><fmt:message key="dlnasettings.servername"/></dt>
            <dd>
                <input type="text" name="dlnaServerName" id="dlnaServerName" value="<c:out value='${command.dlnaServerName}' escapeXml='true'/>"/>
                <c:import url="helpToolTip.jsp"><c:param name="topic" value="dlnaservername"/></c:import>
            </dd>
            <dt><label for="dlnaBaseLANURL" ><fmt:message key="dlnasettings.baselanurl"/></label></dt>
            <dd>
                <input type="text" name="dlnaBaseLANURL" id="dlnaBaseLANURL" value="<c:out value='${command.dlnaBaseLANURL}' />" />
                <c:import url="helpToolTip.jsp"><c:param name="topic" value="dlnalanurl"/></c:import>
            </dd>
            <dt><fmt:message key="usersettings.folderaccess"/></dt>
             <dd>
                <c:forEach items="${command.allMusicFolders}" var="musicFolder">
                    <form:checkbox path="allowedMusicFolderIds" id="musicFolder${musicFolder.id}" value="${musicFolder.id}" cssClass="checkbox"/>
                    <label for="musicFolder${musicFolder.id}">${musicFolder.name}</label>
                </c:forEach>
            </dd>
            <c:if test="${not empty command.allTranscodings}">
                <dt><fmt:message key="playersettings.transcodings"/></dt>
                <dd>
                    <c:forEach items="${command.allTranscodings}" var="transcoding" varStatus="loopStatus">
                        <form:checkbox path="activeTranscodingIds" id="transcoding${transcoding.id}" value="${transcoding.id}" cssClass="checkbox"/>
                        <label for="transcoding${transcoding.id}">${transcoding.name}</label>
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
                    <strong><fmt:message key="playersettings.notranscoder"/></strong>
                </c:if>
            </dd>
        </dl>
    </details>

    <details open>

        <summary class="jpsonic"><fmt:message key="dlnasettings.menu"/></summary>

        <c:if test="${command.showOutlineHelp}">
            <div class="outlineHelp"><fmt:message key="dlnasettings.menuoutline"/></div>
        </c:if>

        <div class="actions">
            <ul class="controls">
                <li>
                    <a href="javaScript:location.href='dlnaSettings.view?reset=topMenu'" title="<fmt:message key='common.reset'/>" class="control reset">
                        <fmt:message key="common.reset"/>
                    </a>
                </li>
            </ul>
        </div>

        <table class="tabular menus">
            <thead>
                <tr>
                    <th><fmt:message key="dlnasettings.menuname"/></th>
                    <th><fmt:message key="dlnasettings.menuenabled"/></th>
                    <th><fmt:message key="dlnasettings.nameOnClient"/></th>
                    <th><fmt:message key="musicfoldersettings.order"/></th>
                </tr>
            </thead>
            <tbody>
                <c:forEach items="${command.topMenuItems}" var="topMenuItem" varStatus="loopStatus">
                    <c:set var="ifDisabled" value='${(topMenuItem.enabled == false) ? "disabledNow" : ""}' />
                    <tr>
                        <td class="${ifDisabled}">${topMenuItem.id.name()}</td>
                        <td class="chk-container ${ifDisabled}"><form:checkbox path="topMenuItems[${loopStatus.count-1}].enabled" /></td>
                        <td class="input-text-container ${ifDisabled}">
                            <form:input path="topMenuItems[${loopStatus.count-1}].name" placeholder="${topMenuItem.defaultName}" value="${topMenuItem.name}" class="subMenuName"/>
                        </td>
                        <td class="${ifDisabled}">
                            <c:if test="${loopStatus.count != 1 && command.topMenuItems[loopStatus.count-1].enabled}">
                                <a href="javaScript:location.href='dlnaSettings.view?upward=${topMenuItem.id.value()}'" class="control up"></a>
                            </c:if>
                        </td>
                    </tr>
                </c:forEach>
            </tbody>
        </table>
    </details>

    <details ${isOpen}>
        <summary class="jpsonic"><fmt:message key="dlnasettings.submenu"/></summary>

        <c:if test="${command.showOutlineHelp}">
            <div class="outlineHelp"><fmt:message key="dlnasettings.submenuoutline"/></div>
        </c:if>

        <div class="actions">
            <ul class="controls">
                <li>
                    <a href="javaScript:location.href='dlnaSettings.view?reset=subMenu'" title="<fmt:message key='common.reset'/>" class="control reset">
                        <fmt:message key="common.reset"/>
                    </a>
                </li>
            </ul>
        </div>

        <table class="tabular sub-menus">
            <thead>
                <tr>
                    <th><fmt:message key="dlnasettings.menuname"/></th>
                    <th><fmt:message key="dlnasettings.menuenabled"/></th>
                    <th><fmt:message key="dlnasettings.nameOnClient"/></th>
                    <th colspan="2">
                        <fmt:message key="dlnasettings.contentoverview"/>
                        <c:import url="helpToolTip.jsp"><c:param name="topic" value="upnpcontent"/></c:import>
                    </th>
                    <th><fmt:message key="dlnasettings.viewopt"/></th>
                </tr>
            </thead>
            <tbody>
                <c:forEach items="${command.subMenuItems}" var="subMenuItem" varStatus="loopStatus">
                    <c:set var="rowInfo" value="${command.subMenuItemRowInfos[subMenuItem.parent]}" />
                    <c:set var="isFirstSubMenu" value="${subMenuItem.parent eq rowInfo.firstChild().parent and subMenuItem.id eq rowInfo.firstChild().id}" />
                    <c:set var="isLastMenu" value="${subMenuItem.parent eq command.topMenuItems[fn:length(command.topMenuItems) - 1].id}" />
                    <c:set var="ifDisabled" value='${(!subMenuItem.enabled or !command.topMenuEnableds.get(subMenuItem.parent)) ? "disabledNow" : ""}' />
                    <c:set var="ifFileStructure" value='${
                            subMenuItem.id eq MenuItemId.MEDIA_FILE
                            or subMenuItem.id eq MenuItemId.MEDIA_FILE_BY_FOLDER
                            or subMenuItem.id eq MenuItemId.INDEX
                            or subMenuItem.id eq MenuItemId.ALBUM_FILE_STRUCTURE
                            or subMenuItem.id eq MenuItemId.ALBUM_FILE_STRUCTURE_BY_FOLDER
                            or subMenuItem.id eq MenuItemId.RANDOM_SONG
                            or subMenuItem.id eq MenuItemId.RECENTLY_ADDED_ALBUM
                            or subMenuItem.id eq MenuItemId.RECENTLY_ADDED_ALBUM_BY_FOLDER
                                ? "F"
                                : ""}' />
                    <c:choose>
                        <c:when test="${isLastMenu}">
                            <tr class="lastMenu">
                        </c:when>
                        <c:otherwise>
                            <tr>
                        </c:otherwise>
                    </c:choose>
                    <c:choose>
                        <c:when test="${isFirstSubMenu}">
                            <td rowspan="${rowInfo.count()}" class="${ifDisabled}">${subMenuItem.parent.name()}</td>
                        </c:when>
                        <c:otherwise>
                        </c:otherwise>
                    </c:choose>
                    <td class="chk-container ${ifDisabled}"><form:checkbox path="subMenuItems[${loopStatus.count-1}].enabled" /></td>
                    <td class="input-text-container ${ifDisabled}">
                        <form:input path="subMenuItems[${loopStatus.count-1}].name" placeholder="${subMenuItem.defaultName}" value="${subMenuItem.name}" class="subMenuName"/>
                    </td>
                    <td class="fileStructure ${ifDisabled}">${ifFileStructure}</td>
                    <td class="hierarchy ${ifDisabled}">
                        <c:choose>
                            <c:when test="${subMenuItem.id eq MenuItemId.MEDIA_FILE}">
                                [Folder ... ] [Song]
                            </c:when>
                            <c:when test="${subMenuItem.id eq MenuItemId.MEDIA_FILE_BY_FOLDER}">
                                [Music Folder] [Folder ... ] [Song]
                            </c:when>
                            <c:when test="${subMenuItem.id eq MenuItemId.INDEX}">
                                [Index] [Folder ... ] [Song]
                            </c:when>
                            <c:when test="${subMenuItem.id eq MenuItemId.INDEX_ID3}">
                                [Index] [Artist] [Album] [Song]
                            </c:when>
                            <c:when test="${subMenuItem.id eq MenuItemId.ALBUM_ARTIST}">
                                [Artist] [Album] [Song]
                            </c:when>
                            <c:when test="${subMenuItem.id eq MenuItemId.ALBUM_ARTIST_BY_FOLDER}">
                                [Music Folder] [Artist] [Album] [Song]
                            </c:when>
                            <c:when test="${subMenuItem.id eq MenuItemId.ALBUM_ID3}">
                                [Album] [Song]
                            </c:when>
                            <c:when test="${subMenuItem.id eq MenuItemId.ALBUM_ID3_BY_FOLDER}">
                                [Music Folder] [Album] [Song]
                            </c:when>
                            <c:when test="${subMenuItem.id eq MenuItemId.ALBUM_FILE_STRUCTURE_BY_FOLDER}">
                                [Music Folder] [Album Folder] [Song]
                            </c:when>
                            <c:when test="${subMenuItem.id eq MenuItemId.ALBUM_FILE_STRUCTURE}">
                                [Album Folder] [Song]
                            </c:when>
                            <c:when test="${subMenuItem.id eq MenuItemId.ALBUM_ID3_BY_GENRE}">
                                [Genre] [Album] [MUSIC]
                            </c:when>
                            <c:when test="${subMenuItem.id eq MenuItemId.ALBUM_ID3_BY_FOLDER_GENRE}">
                                [Music Folder] [Genre] [Album] [MUSIC]
                            </c:when>
                            <c:when test="${subMenuItem.id eq MenuItemId.SONG_BY_GENRE}">
                                [Genre] [MUSIC]
                            </c:when>
                            <c:when test="${subMenuItem.id eq MenuItemId.SONG_BY_FOLDER_GENRE}">
                                [Music Folder] [Genre] [MUSIC]
                            </c:when>
                            <c:when test="${subMenuItem.id eq MenuItemId.AUDIOBOOK_BY_GENRE}">
                                [Genre] [AUDIOBOOK]
                            </c:when>
                            <c:when test="${subMenuItem.id eq MenuItemId.ALBUM_BY_GENRE}">
                                [Genre] [Album Folder] [Song]
                            </c:when>
                            <c:when test="${subMenuItem.id eq MenuItemId.PODCAST_DEFALT}">
                                [Channel] [Episode] [Media(Audio)]
                            </c:when>
                            <c:when test="${subMenuItem.id eq MenuItemId.PLAYLISTS_DEFALT}">
                                [Playlist] [Song]
                            </c:when>
                            <c:when test="${subMenuItem.id eq MenuItemId.RECENTLY_ADDED_ALBUM}">
                                [Album Folder] [Song]
                            </c:when>
                            <c:when test="${subMenuItem.id eq MenuItemId.RECENTLY_ADDED_ALBUM_BY_FOLDER}">
                                [Music Folder] [Album Folder] [Song]
                            </c:when>
                            <c:when test="${subMenuItem.id eq MenuItemId.RECENTLY_TAGGED_ALBUM}">
                                [Album] [Song]
                            </c:when>
                            <c:when test="${subMenuItem.id eq MenuItemId.RECENTLY_TAGGED_ALBUM_BY_FOLDER}">
                                [Music Folder] [Album] [Song]
                            </c:when>
                            <c:when test="${subMenuItem.id eq MenuItemId.RANDOM_SONG}">
                                [Random MUSIC]
                            </c:when>
                            <c:when test="${subMenuItem.id eq MenuItemId.RANDOM_SONG_BY_ARTIST}">
                                [Artist] [Random MUSIC]
                            </c:when>
                            <c:when test="${subMenuItem.id eq MenuItemId.RANDOM_SONG_BY_FOLDER_ARTIST}">
                                [Music Folder] [Artist] [Random MUSIC]
                            </c:when>
                            <c:when test="${subMenuItem.id eq MenuItemId.RANDOM_SONG_BY_GENRE}">
                                [Genre] [Random MUSIC]
                            </c:when>
                            <c:when test="${subMenuItem.id eq MenuItemId.RANDOM_SONG_BY_FOLDER_GENRE}">
                                [Music Folder] [Genre] [Random MUSIC]
                            </c:when>
                            <c:when test="${subMenuItem.id eq MenuItemId.RANDOM_ALBUM}">
                                [Random Album] [MUSIC]
                            </c:when>
                            <c:otherwise>
                            </c:otherwise>
                        </c:choose>
                    </td>
                    <c:choose>
                        <c:when test="${subMenuItem.parent eq MenuItemId.GENRE}">
                            <c:choose>
                                <c:when test="${subMenuItem.id eq MenuItemId.ALBUM_ID3_BY_GENRE}">
                                    <td class="${ifDisabled}">
                                        <form:select path="albumGenreSort">
                                            <c:forEach items="${command.avairableAlbumGenreSort}" var="genreSort">
                                                <c:set var="genreSortViewName">
                                                    <fmt:message key="dlnasettings.dlnasettings.genresort.${fn:toLowerCase(genreSort.name())}"/>
                                                </c:set>
                                                <form:option value="${genreSort}" label="${genreSortViewName}"/>
                                            </c:forEach>
                                        </form:select>
                                        <c:import url="helpToolTip.jsp"><c:param name="topic" value="upnpgenresort"/></c:import>
                                    </td>
                                </c:when>
                                <c:when test="${subMenuItem.id eq MenuItemId.ALBUM_ID3_BY_FOLDER_GENRE}">
                                    <td class="${ifDisabled}">
                                        <c:import url="helpToolTip.jsp"><c:param name="topic" value="upnpgenre1"/></c:import>
                                    </td>
                                </c:when>
                                <c:when test="${subMenuItem.id eq MenuItemId.SONG_BY_GENRE}">
                                    <td class="${ifDisabled}">
                                        <form:select path="songGenreSort">
                                            <c:forEach items="${command.avairableSongGenreSort}" var="genreSort">
                                                <c:set var="genreSortViewName">
                                                    <fmt:message key="dlnasettings.dlnasettings.genresort.${fn:toLowerCase(genreSort.name())}"/>
                                                </c:set>
                                                <form:option value="${genreSort}" label="${genreSortViewName}"/>
                                            </c:forEach>
                                        </form:select>
                                    </td>
                                </c:when>
                                <c:when test="${subMenuItem.id eq MenuItemId.SONG_BY_FOLDER_GENRE
                                        or subMenuItem.id eq MenuItemId.AUDIOBOOK_BY_GENRE}">
                                    <td class="${ifDisabled}">
                                        <c:import url="helpToolTip.jsp"><c:param name="topic" value="upnpgenre2"/></c:import>
                                    </td>
                                </c:when>
                                <c:when test="${subMenuItem.id eq MenuItemId.ALBUM_BY_GENRE}">
                                    <td class="${ifDisabled}">
                                        <c:import url="helpToolTip.jsp"><c:param name="topic" value="upnpsubsonicgenre"/></c:import>
                                    </td>
                                </c:when>
                                <c:otherwise>
                                    <td class="${ifDisabled}"/>
                                </c:otherwise>
                            </c:choose>
                        </c:when>
                        <c:when test="${isFirstSubMenu}">
                            <c:choose>
                                <c:when test="${subMenuItem.parent eq MenuItemId.SHUFFLE}">
                                    <td rowspan="${rowInfo.count()}" class="${ifDisabled}">
                                        <label for="dlnaRandomMax"><fmt:message key="dlnasettings.randommax"/></label>
                                        <form:input type="text" inputmode="numeric" path="dlnaRandomMax" id="dlnaRandomMax" maxlength="4"/>
                                    </td>
                                </c:when>
                                <c:when test="${subMenuItem.parent eq MenuItemId.PLAYLISTS}">
                                    <td rowspan="${rowInfo.count()}" class="${ifDisabled}">
                                        <form:checkbox path="dlnaGuestPublish" id="dlnaGuestPublish"/>
                                        <label for=dlnaGuestPublish><fmt:message key="dlnasettings.guestpublish"/></label>
                                    </td>
                                </c:when>
                                <c:otherwise>
                                    <td rowspan="${rowInfo.count()}" class="${ifDisabled}"></td>
                                </c:otherwise>
                            </c:choose>
                        </c:when>
                        <c:otherwise>
                        </c:otherwise>
                    </c:choose>
                    </tr>
                </c:forEach>
            </tbody>
        </table>
    </details>

    <details ${isOpen}>

        <summary class="jpsonic"><fmt:message key="dlnasettings.search"/></summary>

        <c:if test="${command.showOutlineHelp}">
            <div class="outlineHelp">
                <fmt:message key="dlnasettings.searchoutline"/>
            </div>
        </c:if>

        <dl class="single">
            <dt>
                <fmt:message key="dlnasettings.searchmethod"/>
                <c:import url="helpToolTip.jsp"><c:param name="topic" value="upnpsearchmethod"/></c:import>
            </dt>
            <dd>
                <ul class="playerSettings">
                    <c:forEach items="${UPnPSearchMethod.values()}" var="method">
                        <c:set var="methodName">
                            <fmt:message key="dlnasettings.searchmethod.${fn:toLowerCase(method)}"/>
                        </c:set>
                        <li>
                            <input type="radio" id="radio-${method.name()}" name="searchMethod" value="${method}" ${method eq command.searchMethod ? 'checked' : ''}/>
                            <label for="radio-${method.name()}">${methodName}</label>
                            <c:import url="helpToolTip.jsp"><c:param name="topic" value="searchmethod${fn:toLowerCase(method)}"/></c:import>
                        </li>
                    </c:forEach>
                </ul>
            </dd>
        </dl>

    </details>

    <details ${isOpen}>

        <summary class="jpsonic"><fmt:message key="dlnasettings.upnp.miscellaneous"/></summary>

        <div class="actions">
            <ul class="controls">
                <li><a href="javascript:resetMiscellaneous()" title="<fmt:message key='common.reset'/>" class="control reset"><fmt:message key="common.reset"/></a></li>
            </ul>
        </div>

        <dl>
            <dt><fmt:message key="dlnasettings.upnp.traffic"/></dt>
            <dd>
                <form:checkbox path="dlnaEnabledFilteredIp" id="dlnaEnabledFilteredIp"/>
                <label for=dlnaFilteredIp><fmt:message key="dlnasettings.filteredIp"/></label>
                <input type="text" name="dlnaFilteredIp" id="dlnaFilteredIp"
                        value="<c:out value='${command.dlnaFilteredIp}' escapeXml='true'/>" placeholder="${command.dlnaDefaultFilteredIp}"/>
                <c:import url="helpToolTip.jsp"><c:param name="topic" value="filteredIp"/></c:import>
            </dd>
            <dt>
                <fmt:message key="dlnasettings.upnp.rotation-type"/>
                <c:import url="helpToolTip.jsp"><c:param name="topic" value="upnp-rotation-type"/></c:import>
            </dt>
            <dd>
                <form:select path="dlnaKeyRotationType" id="dlnaKeyRotationType">
                    <c:forEach items="${KeyRotationType.values()}" var="keyRotationType">
                        <c:set var="keyRotationTypeViewName">
                            <fmt:message key="dlnasettings.upnp.rotation-type.${fn:toLowerCase(keyRotationType)}"/>
                        </c:set>
                        <form:option value="${keyRotationType}" label="${keyRotationTypeViewName}"/>
                    </c:forEach>
                </form:select>
            </dd>
            <dt>
                <fmt:message key="dlnasettings.upnp.rotation-period"/>
                <c:import url="helpToolTip.jsp"><c:param name="topic" value="rememberme-rotation-period"/></c:import>
            </dt>
            <dd>
                <form:select path="dlnaKeyRotationPeriod" id="dlnaKeyRotationPeriod">
                    <c:forEach items="${KeyRotationPeriod.values()}" var="keyRotationPeriod">
                        <c:set var="keyRotationPeriodViewName">
                            <fmt:message key="dlnasettings.upnp.rotation-period.${fn:toLowerCase(keyRotationPeriod)}"/>
                        </c:set>
                        <form:option value="${keyRotationPeriod}" label="${keyRotationPeriodViewName}"/>
                    </c:forEach>
                </form:select>
            </dd>
            <dt><fmt:message key='advancedsettings.rememberme.key-lastupdate'/></dt>
            <dd>${command.dlnaKeyLastUpdate}
            </dd>
            <dt>
                <fmt:message key='advancedsettings.rememberme.key-rotate'/>
                <c:import url="helpToolTip.jsp"><c:param name="topic" value="upnp-exec-rotation"/></c:import>
            </dt>
            <dd>
                <div>
                    <input id="rotateNow" type="button" onClick="postRotate()" value="<fmt:message key='advancedsettings.rememberme.exec-key-rotate'/>"/>
                </div>
            </dd>
        </dl>
    </details>

    <div class="submits">
        <input type="submit" value="<fmt:message key='common.save'/>">
    </div>

</form:form>

<form:form id="rotateForm" action="dlnaSettings/rotate" method="post" style="display:none;" />

</body></html>
