<!DOCTYPE html>
<%@ page language="java" contentType="text/html; charset=utf-8" pageEncoding="iso-8859-1"%>
<html>
<head>
<%@ include file="head.jsp"%>
<%@ include file="jquery.jsp"%>
<script src="<c:url value='/dwr/engine.js'/>"></script>
<script src="<c:url value='/dwr/interface/multiService.js'/>"></script>
<script src="<c:url value='/script/utils.js'/>"></script>
<script src="<c:url value='/dwr/interface/scanInfoService.js'/>"></script>
<script src="<c:url value='/script/jpsonic/dialogs.js'/>"></script>
<script src="<c:url value='/script/jpsonic/dialogsTop.js'/>"></script>
<script>

let previousQuery = "";
let instantSearchTimeout;
let drawer;
let toggler;
let main;
let playQueue;

$(document).ready(function(){

    dwr.engine.setErrorHandler(null);

    drawer = document.getElementById('drawer');
    toggler = document.getElementById("toggler");
    main = document.getElementById("main");
    playQueue = window.top.document.getElementById('playQueue');

    onToggleDrawer();

    $('.radio-play').on('click', function(evt) {
        top.playQueue.onPlayInternetRadio($(this).data("id"), 0);
        evt.preventDefault();
    });

    top.initCurrentSongView();

    callScanningStatus();

});

function triggerInstantSearch() {
    if (instantSearchTimeout) {
        window.clearTimeout(instantSearchTimeout);
    }
    instantSearchTimeout = window.setTimeout(executeInstantSearch, 300);
}

function executeInstantSearch() {
    const query = $("#query").val().trim();
    if (query.length > 1 && query != previousQuery) {
        previousQuery = query;
        document.searchForm.submit();
    }
}

function toggleDrawer() {
    top.setDrawerOpened(toggler.checked);
    <c:if test="${model.alternativeDrawer}">
        if(toggler.checked) {
            top.onCloseQueue();
        }
    </c:if>
}

window.onToggleDrawer = function() {
    toggler.checked = !toggler.checked;
    toggleDrawer();
}

window.onCloseDrawer = function() {
    if (toggler.checked) {
        onToggleDrawer();
    }
};

window.onTryCloseDrawer = function() {
    <c:if test="${model.closeDrawer}">
        if (toggler.checked) {
            onToggleDrawer();
        }
    </c:if>
    document.activeElement.blur();
};

window.onQueryFocus = function() {
    $("#query").focus();
}

window.onChangeMainLocation = function(location) {
    $('#main')[0].contentDocument.location = location;
}

function callScanningStatus() {
    scanInfoService.getScanningStatus(getScanningStatusCallback);
}

let retryCallScanningStatus = false;

window.onStartScanning = function() {
    retryCallScanningStatus = true;
    callScanningStatus();
    $('#main').toastmessage("showNoticeToast", "<fmt:message key='main.scanstart'/>");
}

window.callPassiveScanningStatus = function() {
    if(!$("#isScanning").prop('checked')) {
        callScanningStatus();
    }
}

function getScanningStatusCallback(scanInfo) {
    
    let finished = $("#isScanning").prop('checked') && !scanInfo.scanning;
    $("#isScanning").prop('checked', scanInfo.scanning);
    $("#scanningStatus .counter").text(' (' + scanInfo.scanningCount + ')');
    $("#scanningStatus .counter").css("display", scanInfo.phaseName == 'PARSE_FILE_STRUCTURE' ? 'contents' : 'none');
    if(scanInfo.phase != -1) {
        $("#scanningStatus .phase").text("[" + scanInfo.phase + "/" + scanInfo.phaseMax + "]");
        $("#scanningStatus .phaseName").text(scanInfo.phaseName);
    }
    $("#scanningStatus .thread").text(scanInfo.thread > 0 ? 'x' + scanInfo.thread : '');
    const delay = ${model.user.settingsRole ? 4000 : 10000};
    if (scanInfo.scanning) {
       setTimeout("callScanningStatus()", delay);
    } else {
        if (finished) {
            retryCallScanningStatus = false;
            $("#scanningStatus .phase").text('');
            if (document.getElementById("main").contentDocument.location.pathname.split("/").pop().startsWith("musicFolderSettings")) {
                document.getElementById("main").contentDocument.location = "musicFolderSettings.view";
            }
            $('#main').toastmessage("showNoticeToast", "<fmt:message key='main.scanend'/>");
        } else if(retryCallScanningStatus) {
            setTimeout("callScanningStatus()", 2000);
        }
    }
}

window.onChangeCurrentSong = function(song) {
    <c:if test="${model.showCurrentSongInfo}">
        if (song == null) {
            $("#isNowPlaying").prop('checked', false);
        } else {
            $("#isNowPlaying").prop('checked', true);
            if (song.coverArtUrl == null) {
                $(".nowPlaying .coverArt").css("visibility", "hidden");
            } else {
                $(".nowPlaying .coverArt").css("visibility", "visible");
                $(".nowPlaying .coverArt").attr('src', song.coverArtUrl + "&size=60");
            }
            if (song.albumUrl != null) {
                $(".nowPlaying").on('click', function() {
                    window.open(song.albumUrl, 'main');
                });
            }
            $(".nowPlaying #songTitle").text(song.title);
            $(".nowPlaying #dir").text(
                    (song.artist == null ? '' : song.artist) +
                    (song.artist == null || song.album == null ? '' : ' - ') +
                    (song.album == null ? '' : song.album));
        }
    </c:if>
}

<%-- dialogs(dialogsTop.js) --%>
function onOpenDialogVoiceInput() {
    lazyOpenDialogVoiceInput('${model.voiceInputLocale}', "<fmt:message key='common.cancel'/>");
}
window.onShowKeyboardShortcuts = function() {
    lazyOpenDialogKeyboardShortcuts("<fmt:message key='common.cancel'/>");
}

window.onOpenDialogVideoPlayer = function(videoUrl) {
    openDialogVideoPlayer(videoUrl, "<fmt:message key='common.cancel'/>");
}

</script>
</head>

<body class="top-frame">

    <fmt:message key="top.home" var="home" />
    <fmt:message key="top.now_playing" var="nowPlaying" />
    <fmt:message key="top.othersplaying" var="othersPlaying" />
    <fmt:message key="top.starred" var="starred" />
    <fmt:message key="top.playlists" var="playlists" />
    <fmt:message key="top.settings" var="settings" />
    <fmt:message key="top.status" var="status" />
    <fmt:message key="top.podcast" var="podcast" />
    <fmt:message key="top.more" var="more" />
    <fmt:message key="top.help" var="help" />
    <fmt:message key="top.search" var="search" />
    <fmt:message key="top.upload" var="upload" />

    <%-- toggler --%>
    <input type="checkbox" id="toggler" class="jps-input-toggler" autofocus="true" checked onchange="toggleDrawer()" />
    <label for="toggler" class="jps-toggler" role="button" aria-pressed="false" aria-expanded="false" aria-label="Navigation button"> <span class="jps-toggler-line"></span> <span
        class="jps-toggler-line"></span> <span class="jps-toggler-line"></span>
    </label>

    <%-- drawer --%>
    <div id="drawer" class="jps-drawer">

        <div class="jps-musicfolder">
            <c:if test="${fn:length(model.musicFolders) > 1}">
                <select name="musicFolderId" onchange="location='top.view?musicFolderId=' + options[selectedIndex].value;">
                    <option value="-1"><fmt:message key="left.allfolders" /></option>
                    <c:forEach items="${model.musicFolders}" var="musicFolder">
                        <option ${model.selectedMusicFolder.id == musicFolder.id ? "selected" : ""} value="${musicFolder.id}">${fn:escapeXml(musicFolder.name)}</option>
                    </c:forEach>
                </select>
            </c:if>
        </div>

        <input type="checkbox" class="jps-input-without-index" ${model.showIndex ? '' : 'checked'} />
        <c:if test="${not empty model.indexedArtists}">
            <ul class="jps-index">
                <c:forEach items="${model.indexedArtists}" var="entry" varStatus="status">
                    <c:if test="${!model.scanning or (model.scanning and entry.key.index ne '#')}">
                        <c:set var="accesskey" value="${fn:escapeXml(entry.key.index)}" />
                        <c:if test="${model.assignAccesskeyToNumber}">
                            <c:set var="accesskey" value="${status.index%5 == 0 ? Integer.toString(status.index/5) : null}" />
                        </c:if>
                        <c:choose>
                            <c:when test="${!empty accesskey}">
                                <li><a href="#${index.index}" accesskey="${fn:escapeXml(accesskey)}" class="jps-index-key">${fn:escapeXml(entry.key.index)}</a>
                            </c:when>
                            <c:otherwise>
                                <li><a href="#${index.index}" class="jps-index-key">${fn:escapeXml(entry.key.index)}</a>
                            </c:otherwise>
                        </c:choose>
                        <ul>
                            <c:forEach items="${entry.value}" var="artist" varStatus="loop">
                                <sub:url value="main.view" var="mainUrl">
                                    <sub:param name="id" value="${artist.id}" />
                                </sub:url>
                                <li><a target="main" href="${mainUrl}" title="${artist.reading}"><sub:truncateNicely upper="${18}" lower="${25}">${fn:escapeXml(artist.name)}</sub:truncateNicely></a></li>
                            </c:forEach>
                        </ul>
                        </li>
                    </c:if>
                </c:forEach>
                <c:if test="${not empty model.singleSongs}">
                    <li><a target="main" href="home.view?listType=index" class="jps-index-key">more</a></li>
                </c:if>
            </ul>
        </c:if>

        <div class="jps-side-menu">
            <nav>
                <div class="userInfo">
                    <c:if test="${model.showAvatar}">
                        <sub:url value="avatar.view" var="avatarUrl">
                            <sub:param name="username" value="${model.user.username}" />
                        </sub:url>
                        <img src="${avatarUrl}" class="avatar">
                    </c:if>
                    <span class="name"> <c:choose>
                            <c:when test="${model.user.settingsRole}">
                                <a href="personalSettings.view" target="main"
                                    title="<fmt:message key='settingsheader.personal'/>(<c:out value='${model.user.username}' escapeXml='true'/>)">
                                    <span class="name">${model.user.username}</span>
                                </a>
                            </c:when>
                            <c:otherwise>
                                <span class="name">${model.user.username}</span>
                            </c:otherwise>
                        </c:choose>
                    </span>
                </div>
                <form id="logoutForm" action="<c:url value='/logout'/>" method="post" style="display:none;"></form>
                <ul class="menu">
                    <c:if test="${model.putMenuInDrawer}">
                        <li><a href="home.view?" target="main" title="${home}" class="menu-item home">${home}</a></li>
                        <li><a href="starred.view?" target="main" title="${starred}" class="menu-item star">${starred}</a></li>
                        <li><a href="playlists.view?" target="main" title="${playlists}" class="menu-item playlists">${playlists}</a></li>
                        <li><a href="podcastChannels.view?" target="main" title="${podcast}" class="menu-item podcast">${podcast}</a></li>
                    </c:if>
                    <c:if test="${model.user.settingsRole}">
                        <li><a href="settings.view?" target="main" title="${settings}" class="menu-item settings">${settings}</a></li>
                    </c:if>
                    <c:if test="${model.user.uploadRole}">
                        <li><a href="uploadEntry.view?" target="main" title="${upload}" class="menu-item upload">${upload}</a></li>
                    </c:if>
                    <li><a href="help.view?" target="main" title="${help}" class="menu-item about">${help}</a></li>
                    <li>
                        <a href="#" class="menu-item logout" onclick="document.getElementById('logoutForm').submit(); return false;" title="<fmt:message key='top.logout' />">
                            <fmt:message key='top.logout'/>
                        </a>
                    </li>
                </ul>
            </nav>
        </div>

        <aside>
            <c:if test="${not empty model.shortcuts}">
                <ul class="jps-shortcuts">
                    <c:forEach items="${model.shortcuts}" var="shortcut">
                        <sub:url value="main.view" var="mainUrl">
                            <sub:param name="id" value="${shortcut.id}" />
                        </sub:url>
                        <li><a target="main" href="${mainUrl}">${fn:escapeXml(shortcut.name)}</a></li>
                    </c:forEach>
                </ul>
            </c:if>
            <c:if test="${not empty model.radios and model.useRadio eq true}">
                <ul class="jps-radio">
                    <c:forEach items="${model.radios}" var="radio" varStatus="loop">
                        <li><a href="${radio.streamUrl}" target="hidden" data-id="${radio.id}" title="<fmt:message key='common.play'/>" class="control play"><fmt:message key='common.play' /></a> <c:choose>
                                <c:when test="${empty radio.homepageUrl}">
                                    ${fn:escapeXml(radio.name)}
                                </c:when>
                                <c:otherwise>
                                    <a target="_blank" rel="noopener" href="${radio.homepageUrl}">${fn:escapeXml(radio.name)}</a>
                                </c:otherwise>
                            </c:choose></li>
                    </c:forEach>
                </ul>
            </c:if>
        </aside>
    </div>

    <%-- topHeader --%>
    <div class="jps-topHeader">
        <%-- primary menu items --%>
        <c:if test="${not model.putMenuInDrawer}">
            <nav>
                <ul class="menu">
                    <li><a href="home.view?" target="main" title="${home}" class="menu-item home">${home}</a></li>
                    <li><a href="starred.view?" target="main" title="${starred}" class="menu-item star">${starred}</a></li>
                    <li><a href="playlists.view?" target="main" title="${playlists}" class="menu-item playlists">${playlists}</a></li>
                    <li><a href="podcastChannels.view?" target="main" title="${podcast}" class="menu-item podcast">${podcast}</a></li>
                </ul>
            </nav>
        </c:if>
        <%-- scanning --%>
        <input type="checkbox" id="isScanning" class="jps-input-scanning"/>
        <div id="scanningStatus">
            <div class="loader" title="<fmt:message key='main.scanning'/>">
                <div class="thread"></div>
            </div>
            <c:if test="${model.user.adminRole}">
                <a href="scanlog.view" target="main" title="<fmt:message key='settingsheader.scanlog'/>">
            </c:if>
                <span class="phase"></span><span class="phaseName"></span><span class="counter" title="<fmt:message key='main.scannedfiles'/>"></span>
            <c:if test="${model.user.adminRole}">
                </a>
            </c:if>
        </div>
        <%-- search --%>
        <form method="post" action="search.view" target="main" name="searchForm">
            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
            <input required type="text" name="query" id="query" placeholder="${search}" onclick="select();" onkeyup="triggerInstantSearch();">
            <c:choose>
                <c:when test="${model.voiceInputEnabled}">
                    <a href="#" title="${search}" class="control microphone" onclick="onOpenDialogVoiceInput()" >${search}</a>
                </c:when>
                <c:otherwise>
                    <a href="javascript:document.searchForm.submit()" title="${search}" class="control search">${search}</a>
                </c:otherwise>
            </c:choose>
        </form>
        <%-- nowPlaying --%>
        <input type="checkbox" id="isNowPlaying"/>
        <a href="nowPlaying.view?" target="main" title="${nowPlaying}" class="nowPlaying">
            <img class="coverArt">
            <div class="info">
                <div id="songTitle"></div>
                <div id="dir"></div>
            </div>
        </a>
    </div>

    <%-- main --%>
    <c:set var="mainHref" value="nowPlaying.view?" />
    <c:if test="${not empty model.mainView}">
        <c:choose>
            <c:when test="${empty model.selectedItem}">
                <c:set var="mainHref" value="${model.mainView}?toast=yes" />
            </c:when>
            <c:otherwise>
                <c:choose>
                    <c:when test="${'userSettings.view' eq model.mainView}">
                        <c:set var="mainHref" value="${model.mainView}?toast=yes&userIndex=${model.selectedItem}" />
                    </c:when>
                    <c:when test="${'playerSettings.view' eq model.mainView}">
                        <c:set var="mainHref" value="${model.mainView}?toast=yes&id=${model.selectedItem}" />
                    </c:when>
                    <c:otherwise>
                        <c:set var="mainHref" value="${model.mainView}?toast=yes" />
                    </c:otherwise>
                </c:choose>
            </c:otherwise>
        </c:choose>
    </c:if>
    <iframe name="main" id="main" src="${mainHref}" frameborder="no"></iframe>

    <c:if test="${model.newVersionAvailable}">
        <fmt:message key="top.upgradeshort" var="versionNotice"><fmt:param value="${model.brand}"/><fmt:param value="${model.latestVersion}"/></fmt:message>
        <script>
        $('#main').toastmessage('showToast', {
            text     : '${fn:escapeXml(versionNotice)}',
            stayTime : 10000,
            sticky   : false,
            type     : 'warning',
            allowToastClose: true,
            close    : function () {window.open('https://github.com/jpsonic/jpsonic/releases/');}
        });
        </script>
    </c:if>

</body>
</html>
