<!DOCTYPE html>
<%@ page language="java" contentType="text/html; charset=utf-8" pageEncoding="iso-8859-1"%>

<html><head>
<%@ include file="head.jsp" %>
<%@ include file="jquery.jsp" %>
<script src="<c:url value='/script/jpsonic/onSceneChanged.js'/>"></script>
<script src="<c:url value='/script/jpsonic/coverartContainer.js'/>"></script>
<script>

function refresh() {
    window.parent.main.location.href = location.href;
}

function playShuffle() {
  top.playQueue.onPlayShuffle('${model.listType}', ${model.listOffset}, ${model.listSize}, '${model.genre}', '${model.decade}')
}

$(document).ready(function(){
  <c:if test="${not model.musicFoldersExist}">
    $().toastmessage("showNoticeToast", "<fmt:message key="top.missing"/>");
  </c:if>
  <c:if test="${model.isIndexBeingCreated}">
    $().toastmessage("showNoticeToast", "<fmt:message key="home.scan"/>");
  </c:if>
});

</script>
</head>

<body class="mainframe home">

<nav>
    <ul class="breadcrumb">
        <c:choose>
            <c:when test="${not empty model.musicFolder}">
                <li>${fn:escapeXml(model.musicFolder.name)}</li>
            </c:when>
            <c:otherwise>
                <li><fmt:message key='left.allfolders'/></li>
            </c:otherwise>
        </c:choose>
    </ul>
</nav>

<section>
    <c:if test="${not empty model.welcomeTitle}"><h1 class="home">${model.welcomeTitle}</h1></c:if>
    <c:if test="${not empty model.welcomeSubtitle}"><h2>${model.welcomeSubtitle}</h2></c:if>
    <ul class="sibling-pages">

        <c:choose>
            <c:when test="${model.showRate}">
                <c:forTokens items="random newest starred highest frequent recent decade genre alphabetical index" delims=" " var="cat" varStatus="loopStatus">
                    <c:choose>
                       <c:when test="${loopStatus.first}"><li></c:when>
                       <c:otherwise></li><li></c:otherwise>
                    </c:choose>
                    <sub:url var="url" value="home.view">
                        <sub:param name="listType" value="${cat}"/>
                    </sub:url>
                    <c:choose>
                        <c:when test="${model.listType eq cat}">
                            <span class="selected"><fmt:message key="home.${cat}.title"/></span>
                        </c:when>
                        <c:otherwise>
                            <a href="${url}"><fmt:message key="home.${cat}.title"/></a>
                        </c:otherwise>
                    </c:choose>
                    <c:if test="${loopStatus.last}"></li></c:if>
                </c:forTokens>
            </c:when>
            <c:otherwise>
                <%-- without highest(rate) --%>
                <c:forTokens items="random newest starred frequent recent decade genre alphabetical index" delims=" " var="cat" varStatus="loopStatus">
                    <c:choose>
                       <c:when test="${loopStatus.first}"><li></c:when>
                       <c:otherwise></li><li></c:otherwise>
                    </c:choose>
                    <sub:url var="url" value="home.view">
                        <sub:param name="listType" value="${cat}"/>
                    </sub:url>
                    <c:choose>
                        <c:when test="${model.listType eq cat}">
                            <span class="selected"><fmt:message key="home.${cat}.title"/></span>
                        </c:when>
                        <c:otherwise>
                            <a href="${url}"><fmt:message key="home.${cat}.title"/></a>
                        </c:otherwise>
                    </c:choose>
                    <c:if test="${loopStatus.last}"></li></c:if>
                </c:forTokens>
            </c:otherwise>
        </c:choose>
    </ul>
</section>

<div class="actions">
    <ul class="controls">
        <li><a href="javascript:refresh()" title="<fmt:message key='common.refresh'/>" class="control refresh"><fmt:message key="common.refresh"/></a></li>
        <li><a href="javascript:playShuffle()" title="<fmt:message key='home.shuffle'/>" class="control shuffle"><fmt:message key="home.shuffle"/></a></li>
    </ul>
    <c:set var="isFootPager" value="false" />
    <%@ include file="homePager.jsp" %>
</div>




    <c:if test="${not empty model.welcomeMessage}">
        <div class="welcome">${model.welcomeMessage}</div>
    </c:if>

<div class="coverart-container" id ="coverart-container">
    <c:forEach items="${model.albums}" var="album" varStatus="loopStatus">

        <c:set var="albumTitle">
            <c:choose>
                <c:when test="${empty album.albumTitle}">
                    <fmt:message key="common.unknown"/>
                </c:when>
                <c:otherwise>
                    ${album.albumTitle}
                </c:otherwise>
            </c:choose>
        </c:set>

        <c:set var="captionCount" value="2"/>

        <c:if test="${not empty album.playCount}">
            <c:set var="caption3"><fmt:message key="home.playcount"><fmt:param value="${album.playCount}"/></fmt:message></c:set>
            <c:set var="captionCount" value="3"/>
        </c:if>
        <c:if test="${not empty album.lastPlayed}">
            <fmt:parseDate value="${album.lastPlayed}" type="both" pattern="yyyy-MM-dd" var="parsedDate" />
            <fmt:formatDate value="${parsedDate}" pattern="yyyy-MM-dd" var="lastPlayedDate" />
            <c:set var="caption3"><fmt:message key="home.lastplayed"><fmt:param value="${lastPlayedDate}"/></fmt:message></c:set>
            <c:set var="captionCount" value="3"/>
        </c:if>
        <c:if test="${not empty album.created}">
            <fmt:parseDate value="${album.created}" type="both" pattern="yyyy-MM-dd" var="parsedDate" />
            <fmt:formatDate value="${parsedDate}" pattern="yyyy-MM-dd" var="creationDate" />
            <c:set var="caption3"><fmt:message key="home.created"><fmt:param value="${creationDate}"/></fmt:message></c:set>
            <c:set var="captionCount" value="3"/>
        </c:if>
        <c:if test="${not empty album.year}">
            <c:set var="caption3" value="${album.year}"/>
            <c:set var="captionCount" value="3"/>
        </c:if>
        <div class="albumThumb">
            <c:import url="coverArt.jsp">
                <c:param name="albumId" value="${album.id}"/>
                <c:param name="caption1" value="${fn:escapeXml(album.albumTitle)}"/>
                <c:param name="caption2" value="${fn:escapeXml(album.artist)}"/>
                <c:param name="caption3" value="${caption3}"/>
                <c:param name="captionCount" value="${captionCount}"/>
                <c:param name="coverArtSize" value="${model.coverArtSize}"/>
                <c:param name="showLink" value="true"/>
                <c:param name="appearAfter" value="${loopStatus.count * 30}"/>
                <c:param name="hideOverflow" value="true"/>
            </c:import>

            <c:if test="${not empty album.rating}">
                <c:import url="rating.jsp">
                    <c:param name="readonly" value="true"/>
                    <c:param name="rating" value="${album.rating}"/>
                </c:import>
            </c:if>
    
        </div>
    </c:forEach>
</div>

<c:set var="isOpen" value='${model.isOpenDetailIndex ? "open" : ""}' />

<c:if test="${not empty model.indexedArtists}">
    <c:forEach items="${model.indexedArtists}" var="entry" varStatus="status">
        <details ${isOpen}>
            <c:set var="accesskey" value="${fn:escapeXml(entry.key.index)}" />
            <c:if test="${model.assignAccesskeyToNumber}">
                <c:set var="accesskey" value="${status.index%5 == 0 ? Integer.toString(status.index/5) : null}" />
            </c:if>
            <c:choose>
                <c:when test="${!empty accesskey}">
                    <summary accesskey="${fn:escapeXml(accesskey)}">${fn:escapeXml(entry.key.index)}</summary>
                </c:when>
                <c:otherwise>
                    <summary>${fn:escapeXml(entry.key.index)}</summary>
                </c:otherwise>
            </c:choose>
            <ul class="anchorList">
                <c:forEach items="${entry.value}" var="artist" varStatus="loop">
                    <sub:url value="main.view" var="mainUrl">
                        <sub:param name="id" value="${artist.id}"/>
                    </sub:url>
                    <li><a target="main" href="${mainUrl}" title="${artist.reading}">${fn:escapeXml(artist.name)}</a></li>
                </c:forEach>
            </ul>
        </details>
    </c:forEach>
</c:if>

<c:if test="${not empty model.singleSongs}">
    <details>
        <summary><fmt:message key="home.index.child"/></summary>
        <ul>
            <c:forEach items="${model.singleSongs}" var="song">
                <li class="fileControls">
                    ${fn:escapeXml(song.title)}
                    <c:import url="playButtons.jsp">
                        <c:param name="id" value="${song.id}"/>
                        <c:param name="playEnabled" value="${model.user.streamRole and not model.partyMode}"/>
                        <c:param name="addEnabled" value="${model.user.streamRole and not model.partyMode}"/>
                        <c:param name="downloadEnabled" value="${model.user.downloadRole and model.showDownload}"/>
                        <c:param name="video" value="${song.isVideo}"/>
                    </c:import>
                </li>
            </c:forEach>
        </ul>
    </details>
</c:if>

<c:set var="isFootPager" value="true" />
<%@ include file="homePager.jsp" %>

</body></html>
