<%@ page language="java" contentType="text/html; charset=utf-8" pageEncoding="iso-8859-1"%>
<%--@elvariable id="model" type="Map"--%>
<%@ page trimDirectiveWhitespaces="true"%>

<html>
<head>
<%@ include file="head.jsp"%>
<%@ include file="jquery.jsp"%>
</head>
<body class="mainframe settings shareSettings">

    <c:import url="settingsHeader.jsp">
        <c:param name="cat" value="share" />
        <c:param name="toast" value="${settings_toast}" />
        <c:param name="restricted" value="${not model.user.adminRole}" />
        <c:param name="useRadio" value="${model.useRadio}"/>
    <c:param name="useDatabaseSettings" value="${model.useDatabaseSettings}"/>
        <c:param name="existsShare" value="${model.shareCount ne 0}"/>
    </c:import>

    <form:form method="post" action="shareSettings.view">

		<c:if test="${not empty model.shareInfos}">
	        <table class="tabular share-settings">
	            <thead>
	                <tr>
	                    <th><fmt:message key="sharesettings.name" /></th>
	                    <th><fmt:message key="sharesettings.details" /></th>
	                    <th><fmt:message key="common.delete" /></th>
	                </tr>
	            </thead>
	            <tbody>
	                <c:forEach items="${model.shareInfos}" var="shareInfo" varStatus="loopStatus">
	                    <c:set var="share" value="${shareInfo.share}" />
	                    <c:url value="main.view" var="albumUrl">
	                        <c:param name="id" value="${shareInfo.dir.id}" />
	                    </c:url>
	                    <tr>
	                        <td>
	                            <a href="${shareInfo.shareUrl}" target="_blank">${share.name}</a>
	                        </td>
	                        <td>
	                            <dl>
	                                <dt><fmt:message key="sharesettings.owner" /></dt>
	                                <dd>${fn:escapeXml(share.username)}</dd>
	                                <dt><fmt:message key="sharesettings.description" /></dt>
	                                <dd><input type="text" name="description[${share.id}]" value="${share.description}" /></dd>
	                                <dt><fmt:message key="sharesettings.lastvisited" /></dt>
                                    <dd>
                                        <fmt:parseDate value="${share.lastVisitedWithZone}" type="date" pattern="yyyy-MM-dd'T'HH:mm:ss" var="parsedDate" />
                                        <fmt:formatDate value="${parsedDate}" pattern="yyyy-MM-dd HH:mm:ss" />
                                    </dd>
                                    <dt><fmt:message key="sharesettings.visits" /></dt>
                                    <dd>${share.visitCount}</dd>
                                    <dt><fmt:message key="sharesettings.files" /></dt>
                                    <dd><a href="${albumUrl}" title="${shareInfo.dir.name}"><sub:truncateNicely upper="30">${fn:escapeXml(shareInfo.dir.name)}</sub:truncateNicely></a></dd>
                                    <dt><fmt:message key="sharesettings.expires" /></dt>
                                    <dd>
                                        <fmt:parseDate value="${share.expiresWithZone}" type="date" pattern="yyyy-MM-dd'T'HH:mm:ss" var="parsedDate" />
                                        <fmt:formatDate value="${parsedDate}" pattern="yyyy-MM-dd HH:mm:ss" />
                                    </dd>
	                                <dt><fmt:message key="sharesettings.expirein" /></dt>
	                                <dd>
	                                    
	                                    <label><input type="radio" name="expireIn[${share.id}]" value="7"> <fmt:message key="sharesettings.expirein.week" /></label>
	                                    <label><input type="radio" name="expireIn[${share.id}]" value="30"> <fmt:message key="sharesettings.expirein.month" /></label>
	                                    <label><input type="radio" name="expireIn[${share.id}]" value="365"> <fmt:message key="sharesettings.expirein.year" /></label>
	                                    <label><input type="radio" name="expireIn[${share.id}]" value="0"> <fmt:message key="sharesettings.expirein.never" /></label>
	                                </dd>
	                        	</dl>
	                        </td>
	                        <td>
	                            <input type="checkbox" name="delete[${share.id}]" />
	                        </td>
	                    </tr>
	                </c:forEach>
	            </tbody>
	        </table>

			<div class="deleteExpired">
		        <input type="checkbox" id="deleteExpired" name="deleteExpired" class="checkbox" />
		        <label for="deleteExpired"><fmt:message key="sharesettings.deleteexpired" /></label>
	        </div>
        </c:if>

        <div class="submits">
        	<c:if test="${not empty model.shareInfos}">
	            <input type="submit" value="<fmt:message key='common.save'/>">
            </c:if>
        </div>

    </form:form>

</body>
</html>
