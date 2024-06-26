<%--@elvariable id="model" type="java.util.Map"--%>
<%@ page language="java" contentType="text/html; charset=utf-8" pageEncoding="iso-8859-1" %>

<html>
<head>
    <%@ include file="head.jsp" %>
    <meta name="og:type" content="album"/>
    <script src="<c:url value='/script/mediaelement/mediaelement-and-player.min.js'/>"></script>
    <script src="<c:url value='/script/mediaelement/playlist.min.js'/>"></script>
    <link type="text/css" rel="stylesheet" href="<c:url value='/script/mediaelement/playlist.min.css'/>">
    <c:if test="${not empty model.songs}">
        <meta name="og:title" content="${fn:escapeXml(model.songs[0].artist)} &mdash; ${fn:escapeXml(model.songs[0].albumName)}"/>
        <meta name="og:image" content="${model.songs[0].coverArtUrl}"/>
    </c:if>
</head>

<body class="mainframe externalPlayer">
<div class="external box">
    <div class="header">
        <h1>
            <c:choose>
                <c:when test="${empty model.share or empty model.songs}">
                    Sorry, the content is not available.
                </c:when>
                <c:otherwise>
                    ${empty model.share.description ? model.songs[0].artist : fn:escapeXml(model.share.description)}
                </c:otherwise>
            </c:choose>
        </h1>
        <div>
            <h2>${empty model.share.description ? model.songs[0].albumName : fn:escapeXml(model.share.username)}</h2>
        </div>
    </div>

    <audio id='player'>
        <c:forEach items="${model.songs}" var="song" varStatus="loopStatus">
            <source
                    src="${song.streamUrl}"
                    title="${fn:escapeXml(song.title)}"
                    type="${song.getMediaType()=='MUSIC'?'audio':'video'}/${fn:escapeXml(song.getFormat())}"
                    data-playlist-thumbnail="${song.coverArtUrl}"
                    data-playlist-description="${fn:escapeXml(song.artist)}"
            >
        </c:forEach>
    </audio>

    <div>Streaming by <a href="https://github.com/jpsonic/jpsonic" rel="noopener noreferrer" target="_blank">Jpsonic</a></div>
</div>

<script>
    new MediaElementPlayer('player', {
        features: ['playpause', 'playlist', 'current', 'progress', 'duration', 'volume'],
        currentMessage: "",
        audioWidth: 600,
    });
</script>
</body>
</html>
