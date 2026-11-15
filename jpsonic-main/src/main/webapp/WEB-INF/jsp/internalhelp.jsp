<!DOCTYPE html>
<%@ page language="java" contentType="text/html; charset=utf-8" pageEncoding="iso-8859-1"%>

<html><head>
<%@ include file="head.jsp" %>
<script src="<c:url value='/script/utils.js'/>"></script>
<script src="<c:url value='/script/jpsonic/onSceneChanged.js'/>"></script>
</head>
<body class="mainframe settings internalhelp">

<c:import url="helpHeader.jsp">
    <c:param name="cat" value="internalhelp"/>
    <c:param name="isAdmin" value="${model.admin}"/>
</c:import>

<details>
    <summary class="
            <c:choose>
                <c:when test='${not empty model.gc}'>
                    statusOK
                </c:when>
                <c:otherwise>
                    statusNG
                </c:otherwise>
            </c:choose>
        ">
        <fmt:message key="internalhelp.platforminfo"/>
    </summary>
    <dl>
        <dt><fmt:message key="internalhelp.platformname"/></dt>
        <dd>${model.platformName}</dd>
        <dt><fmt:message key="internalhelp.osname"/></dt>
        <dd>${model.osName}</dd>
        <dt><fmt:message key="internalhelp.osversion"/></dt>
        <dd>${model.osVersion}</dd>
        <dt><fmt:message key="internalhelp.osarc"/></dt>
        <dd>${model.osArc}</dd>
        <dt><fmt:message key="internalhelp.javaversion"/></dt>
        <dd>${model.javaVersion}</dd>
        <dt><fmt:message key="internalhelp.memory"/></dt>
        <dd><sub:formatBytes bytes="${model.usedMemory}"/> / <sub:formatBytes bytes="${model.totalMemory}"/></dd>
        <dt><fmt:message key="internalhelp.gc"/></dt>
        <dd>${model.gc}</dd>
        <dt><fmt:message key="internalhelp.applicationserver"/></dt>
        <dd>${model.applicationServer}</dd>
    </dl>
</details>

<details>
    <summary class="
        <c:choose>
            <c:when test='${model.dbIsLegacy}'>
                <c:choose>
                    <c:when test='${model.dbLogSizeBytes < 268435456}'>
                        statusOK
                    </c:when>
                    <c:otherwise>
                        statusNG
                    </c:otherwise>
                </c:choose>
            </c:when>
            <c:otherwise>
                statusOK
            </c:otherwise>
        </c:choose>
    ">
        <fmt:message key="internalhelp.productsinfo"/>
    </summary>
    <c:choose>
        <c:when test='${model.dbIsLegacy}'>
            <ul>
                <li>
                    <c:choose>
                        <c:when test="${model.dbLogSizeBytes < 268435456}">
                            <fmt:message key="internalhelp.dblogsize.ok"/>
                        </c:when>
                        <c:otherwise>
                            <fmt:message key="internalhelp.dblogsize.warn"/>
                        </c:otherwise>
                    </c:choose>
                </li>
            </ul>
        </c:when>
    </c:choose>
    <dl>
        <dt><fmt:message key="internalhelp.luceneversion"/></dt>
        <dd>${model.indexLuceneVersion}</dd>
        <dt><fmt:message key="internalhelp.dbdrivername"/></dt>
        <dd>${model.dbDriverName}</dd>
        <dt><fmt:message key="internalhelp.dbdriverversion"/></dt>
        <dd>${model.dbDriverVersion}</dd>
        <dt><fmt:message key="internalhelp.dbserverversion"/></dt>
        <dd>${model.dbServerVersion}</dd>
        <c:if test="${model.dbIsLegacy}">
            <dt><fmt:message key="internalhelp.dbdirectorysize"/></dt>
            <dd>${model.dbDirectorySize}</dd>
            <dt><fmt:message key="internalhelp.dblogsize"/></dt>
            <dd>${model.dbLogSize}</dd>
        </c:if>
    </dl>
</details>

<details>
    <c:set var="failCount" value="0" scope="page" />
    <c:forEach var="musicFolderStatistics" items="${model.fsMusicFolderStatistics}">
        <c:if test="${!musicFolderStatistics.value.readable}">
            <c:set var="failCount" value="${count + 1}" scope="page"/>
        </c:if>
    </c:forEach>
    <summary class="
        <c:choose>
            <c:when test='${failCount eq 0}'>
                        statusOK
            </c:when>
            <c:otherwise>
                        statusNG
            </c:otherwise>
        </c:choose>
    ">
        <fmt:message key="internalhelp.filesystem"/>
    </summary>
    <ul>
        <c:forEach var="musicFolderStatistics" items="${model.fsMusicFolderStatistics}">
            <li>
                <c:choose>
                    <c:when test="${musicFolderStatistics.value.readable}">
                        <fmt:message key="internalhelp.folderisreadable.ok"><fmt:param value="${musicFolderStatistics.key}"/></fmt:message>
                    </c:when>
                    <c:otherwise>
                        <fmt:message key="internalhelp.folderisreadable.warn"><fmt:param value="${musicFolderStatistics.key}"/></fmt:message>
                    </c:otherwise>
                </c:choose>
            </li>
        </c:forEach>
    </ul>
    <dl>
        <dt><fmt:message key="internalhelp.fsusage"/></dt>
        <dd>${model.fsHomeUsableSpace} / ${model.fsHomeTotalSpace}</dd>
        <dt><fmt:message key="internalhelp.fshomesize"/></dt>
        <dd>${model.fsHomeDirectorySize}</dd>

        <c:forEach var="musicFolderStatistics" items="${model.fsMusicFolderStatistics}">
            <dt><fmt:message key="internalhelp.folderisreadable"><fmt:param value="${musicFolderStatistics.key}"/></fmt:message></dt>
            <dd>${musicFolderStatistics.value.readable}</dd>
            <dt><fmt:message key="internalhelp.folderiswritable"><fmt:param value="${musicFolderStatistics.key}"/></fmt:message></dt>
            <dd>${musicFolderStatistics.value.writable}</dd>
            <dt><fmt:message key="internalhelp.folderfsusage"><fmt:param value="${musicFolderStatistics.key}"/></fmt:message></dt>
            <dd>${musicFolderStatistics.value.freeFilesystemSizeBytes} / ${musicFolderStatistics.value.totalFilesystemSizeBytes}</dd>
        </c:forEach>
    </dl>
</details>

<details>
    <summary class="
        <c:choose>
            <c:when test='${model.ffprobeReadable and model.ffprobeExecutable && model.ffmpegReadable and model.ffmpegExecutable}'>
                statusOK
            </c:when>
            <c:otherwise>
                statusNG
            </c:otherwise>
        </c:choose>
    ">
        <fmt:message key="internalhelp.transcoding"/>
    </summary>
    <ul>
        <li>
            <c:choose>
                <c:when test="${model.ffprobeReadable and model.ffprobeExecutable}">
                    <fmt:message key="internalhelp.tool.ok"><fmt:param value="ffprobe"/></fmt:message>
                </c:when>
                <c:otherwise>
                    <fmt:message key="internalhelp.tool.warn"><fmt:param value="ffprobe"/></fmt:message>
                </c:otherwise>
            </c:choose>
        </li>
        <li>
            <c:choose>
                <c:when test="${model.ffmpegReadable and model.ffmpegExecutable}">
                    <fmt:message key="internalhelp.tool.ok"><fmt:param value="ffmpeg"/></fmt:message>
                </c:when>
                <c:otherwise>
                    <fmt:message key="internalhelp.tool.warn"><fmt:param value="ffmpeg"/></fmt:message>
                </c:otherwise>
            </c:choose>
        </li>
    </ul>
    <dl>
        <dt><fmt:message key="internalhelp.tool.path"><fmt:param value="FFprobe"/></fmt:message></dt>
        <dd>${model.ffprobePath}</dd>
        <dt><fmt:message key="internalhelp.tool.isreadable"><fmt:param value="FFprobe"/></fmt:message></dt>
        <dd>${model.ffprobeReadable}</dd>
        <dt><fmt:message key="internalhelp.tool.isexecutable"><fmt:param value="FFprobe"/></fmt:message></dt>
        <dd>${model.ffprobeExecutable}</dd>
        <dt><fmt:message key="internalhelp.tool.path"><fmt:param value="FFmpeg"/></fmt:message></dt>
        <dd>${model.ffmpegPath}</dd>
        <dt><fmt:message key="internalhelp.tool.isreadable"><fmt:param value="FFmpeg"/></fmt:message></dt>
        <dd>${model.ffmpegReadable}</dd>
        <dt><fmt:message key="internalhelp.tool.isexecutable"><fmt:param value="FFmpeg"/></fmt:message></dt>
        <dd>${model.ffmpegExecutable}</dd>
        <dt><fmt:message key="internalhelp.tool.version"><fmt:param value="FFmpeg"/></fmt:message></dt>
        <dd><pre>${model.ffmpegVersion}</pre></dd>
    </dl>
</details>

<details>
    <summary class="
            <c:choose>
                <c:when test='${model.localeDefaultCharsetSupportsUtf8 && model.localeFileEncodingSupportsUtf8}'>
                    statusOK
                </c:when>
                <c:otherwise>
                    statusNG
                </c:otherwise>
            </c:choose>
    ">
        <fmt:message key="internalhelp.locale"/>
    </summary>
    <ul>
        <li>
            <c:choose>
                <c:when test="${model.localeDefaultCharsetSupportsUtf8}">
                    <fmt:message key="internalhelp.defaultcharset.ok"/>
                </c:when>
                <c:otherwise>
                    <fmt:message key="internalhelp.defaultcharset.warn"/>
                </c:otherwise>
            </c:choose>
        </li>
        <li>
            <c:choose>
                <c:when test="${model.localeFileEncodingSupportsUtf8}">
                    <fmt:message key="internalhelp.file.encoding.ok"/>
                </c:when>
                <c:otherwise>
                    <fmt:message key="internalhelp.file.encoding.warn"/>
                </c:otherwise>
            </c:choose>
        </li>
        <c:if test="${not empty model.localeLang and not model.localeLangSupportsUtf8}">
            <li>The LANG environment variable is defined but appears to disable UTF-8 support. International characters may be partially supported.</li>
        </c:if>
        <c:if test="${not empty model.localeLcAll and not model.localeLcAllSupportsUtf8}">
            <li>The LC_ALL environment variable is defined but appears to disable UTF-8 support. International characters may be partially supported.</li>
        </c:if>
    </ul>
    <dl>
        <dt><fmt:message key="internalhelp.langvar"/></dt>
        <dd>${model.localeLang}</dd>
        <dt><fmt:message key="internalhelp.lcallvar"/></dt>
        <dd>${model.localeLcAll}</dd>

        <dt><fmt:message key="internalhelp.defaultlocale"/></dt>
        <dd>${model.localeDefault}</dd>
        <dt><fmt:message key="internalhelp.user.language"/></dt>
        <dd>${model.localeUserLanguage}</dd>
        <dt><fmt:message key="internalhelp.user.country"/></dt>
        <dd>${model.localeUserCountry}</dd>
        <dt><fmt:message key="internalhelp.defaultcharset"/></dt>
        <dd>${model.localeDefaultCharset}</dd>
        <dt><fmt:message key="internalhelp.file.encoding"/></dt>
        <dd>${model.localeFileEncoding}</dd>
        <dt><fmt:message key="internalhelp.sun.jnu.encoding"/></dt>
        <dd>${model.localeSunJnuEncoding}</dd>
        <dt><fmt:message key="internalhelp.sun.io.unicode.encoding"/></dt>
        <dd>${model.localeSunIoUnicodeEncoding}</dd>
        <dt><fmt:message key="internalhelp.defaultzoneoffset"/></dt>
        <dd>${model.localeDefaultZoneOffset}</dd>
    </dl>
</details>

<details open>
    <summary class="statusOK">
        <fmt:message key="internalhelp.statistics"/>
    </summary>
    <c:if test="${model.stats.size() > 0}">
        <table class="tabular stats">
            <caption>
                <fmt:message key="internalhelp.lastscandate"/> : 
                <fmt:parseDate value="${model.stats[0].executed}" type="both" pattern="yyyy-MM-dd'T'HH:mm:ss" var="parsedDate" />
                <fmt:formatDate value="${parsedDate}" pattern="yyyy-MM-dd HH:mm:ss" />
            </caption>
            <thead>
                <tr>
                    <th><fmt:message key="internalhelp.foldername"/></th>
                    <th><fmt:message key="internalhelp.artistcount"/></th>
                    <th><fmt:message key="internalhelp.albumcount"/></th>
                    <th><fmt:message key="internalhelp.songcount"/></th>
                    <th><fmt:message key="internalhelp.videocount"/></th>
                    <th><fmt:message key="internalhelp.totaldurationseconds"/></th>
                    <th><fmt:message key="internalhelp.totalsizebytes"/></th>
                </tr>
            </thead>
            <tbody>
                <c:forEach items="${model.stats}" var="stat" varStatus="loopStatus">
                    <tr>
                        <td>${stat.folderName}</td>
                        <td>${stat.artistCount}</td>
                        <td>${stat.albumCount}</td>
                        <td>${stat.songCount}</td>
                        <td>${stat.videoCount}</td>
                        <td>${stat.size}</td>
                        <td>${stat.duration}</td>
                    </tr>
                </c:forEach>
            </tbody>
        </table>
    </c:if>
</details>

<div class="submits">
    <input type="button" onClick="location.href='internalhelp.view?'" value="<fmt:message key='common.refresh'/>" />
</div>

</body>
</html>
