<%@ page language="java" contentType="text/html; charset=utf-8" pageEncoding="iso-8859-1" %>
<%--@elvariable id="command" type="com.tesshu.jpsonic.controller.form.DatabaseSettingsCommand"--%>

<html>
<head>
<%@ include file="head.jsp" %>
<%@ include file="jquery.jsp" %>
<%@ page import="com.tesshu.jpsonic.infrastructure.db.DatabaseConfiguration" %>

<script>

const driverNamePostgres = "org.postgresql.Driver";
const driverNameMariaDB = "org.mariadb.jdbc.Driver";
const driverNameMySQL = "com.mysql.jdbc.Driver";

function getJdbcURL(name, host, port, dbName) {
    dbName = !dbName ? "yourDBName" : dbName;
    if(name == 'Postgres') {
        return "jdbc:postgresql://"
                + (!host ? "127.0.0.1" : host) + ":" + (!port ? "5432" : port)
                + "/" + dbName + "?stringtype=unspecified";
    } else if(name == 'MariaDB') {
        return "jdbc:mariadb://"
                + (!host ? "127.0.0.1" : host) + ":" + (!port ? "3306" : port)
                + "/" + dbName;
    } else if(name == 'MySQL') {
        return "jdbc:mysql://"
                + (!host ? "127.0.0.1" : host) + ":" + (!port ? "3306" : port)
                + "/" + dbName + "?serverTimezone=UTC";
    }
    return null;
}

function updateHelperContent(name) {

    const selectedType = $('select#configType').val();
    var driverName;
    if(name == 'Postgres') {
        driverName = driverNamePostgres;
        $("#mysqlVarcharMaxlength").val("0");
        $("#usertableQuote").val("\"");
    } else if(name == 'MariaDB') {
        driverName = driverNameMariaDB;
        $("#mysqlVarcharMaxlength").val("512");
        $("#usertableQuote").val("");
    } else if(name == 'MySQL') {
        driverName = driverNameMySQL;
        $("#mysqlVarcharMaxlength").val("512");
        $("#usertableQuote").val("");
    }
    const jdbcUrl = getJdbcURL(name, $("#helperHost").val(), $("#helperPort").val(), $("#helperDbName").val());
    const jdbcUser = !$("#helperUser").val() ? 'yourDBUser' : $("#helperUser").val();
    const jdbcPass = !$("#helperPass").val() ? 'yourDBPass' : $("#helperPass").val();

    if(selectedType == 'URL') {
        $("#embedDriver").val(driverName);
        $("#embedUrl").val(jdbcUrl);
        $("#embedUsername").val(jdbcUser);
        $("#embedPassword").val(jdbcPass);
    } else if (selectedType == 'JNDI'){
        const helperJndiResouceName = !$("#helperJndiResouceName").val() ? 'yourResouceName' : $("#helperJndiResouceName").val();
        $("#JNDIName").val(helperJndiResouceName);
        $("#jndiResource").val(
                "<Resource \n"
                 + "\tauth=\"Container\"\n"
                 + "\tname=\"" + helperJndiResouceName + "\"\n"
                 + "\tfactory=\"com.zaxxer.hikari.HikariJNDIFactory\"\n"
                 + "\ttype=\"javax.sql.DataSource\"\n"
                 + "\tdriverClassName=\"" + driverName + "\" \n"
                 + "\tjdbcUrl=\"" + jdbcUrl + "\" \n"
                 + "\tdataSource.user=\"" + jdbcUser + "\"\n"
                 + "\tdataSource.password=\"" + jdbcPass + "\"\n"
                 + "\tconnectionTimeout=\"${DatabaseConfiguration.DS_CONNECTION_TIMEOUT}\"\n"
                 + "\tminimumIdle=\"${DatabaseConfiguration.DS_MINIMUM_IDLE}\"\n"
                 + "\tmaximumPoolSize=\"${DatabaseConfiguration.DS_MAXIMUM_POOLSIZE}\"\n"
                 + "\tmaxLifetime=\"${DatabaseConfiguration.DS_MAX_LIFE_TIME}\"\n"
                 + "\tidleTimeout=\"${DatabaseConfiguration.DS_IDLE_TIMEOUT}\" /> \n");
    }
}

function updateShownOptions() {
    $(".hideawayDatabaseOptions").hide();
    const value = $('select#configType').val();
    var objToShow = $("#" + value + "DatabaseOptions");
    if (objToShow.length) {
        objToShow.show();
    }
    if(value == 'HOST') {
        $("#helper").css({'display': 'none'});
    } else {
        $("#helper").css({'display': 'block'});
        $("#nonHOSTDatabaseOptions").show();
        $(".jndiHelperItem").css({'display': (value == 'JNDI' ? 'block' : 'none')});
        if (value == 'URL') {
            $("#driverDt").addClass('bottomDT');
            $("#driverDd").addClass('bottomDD');
        } else {
            $("#driverDt").removeClass('bottomDT');
            $("#driverDd").removeClass('bottomDD');
        }
    }
}

$(document).ready(function () {
    updateShownOptions();
    $('select#configType').on('change', function () {
        updateShownOptions();
    });
});
</script>
</head>
<body class="mainframe settings databaseSettings">

<c:import url="settingsHeader.jsp">
    <c:param name="cat" value="database"/>
    <c:param name="toast" value="${settings_toast}"/>
    <c:param name="useRadio" value="${command.useRadio}"/>
    <c:param name="useDatabaseSettings" value="${command.useDatabaseSettings}"/>
    <c:param name="existsShare" value="${command.shareCount ne 0}"/>
</c:import>

<c:import url="outlineHelpSelector.jsp">
    <c:param name="targetView" value="databaseSettings.view"/>
    <c:param name="showOutlineHelp" value="${command.showOutlineHelp}"/>
</c:import>

<c:if test="${command.showOutlineHelp}">
    <div class="outlineHelp">
        <fmt:message key="databasesettings.moreinfo"/><br>
        <strong><fmt:message key="databasettings.restartRequired"/></strong>
    </div>
</c:if>

<form:form modelAttribute="command" action="databaseSettings.view" method="post">

    <dl class="topSelectorContainer2">
        <dt><fmt:message key="databasesettings.configtype"/></dt>
        <dd>
            <form:select path="configType" id="configType">
                <form:option value="HOST" label="HOST(HSQLDB)"/>
                <form:option value="URL" label="URL"/>
                <form:option value="JNDI" label="JNDI"/>
            </form:select>
            <c:import url="helpToolTip.jsp"><c:param name="topic" value="databaseConfigType"/></c:import>
        </dd>
    </dl>

    <details id="helper" class="helper">
        <summary class="jpsonic">Resource Settings Helper</summary>

		<c:if test="${command.showOutlineHelp}">
		    <div class="outlineHelp">
		        <fmt:message key="databasesettings.helperoutline"/><br>
		    </div>
		</c:if>

        <dl>
            <dt>Database Host</dt>
            <dd><input type="text" id="helperHost"></dd>
            <dt>Database Port</dt>
            <dd><input type="text" id="helperPort"></dd>
            <dt>Database Name</dt>
            <dd><input type="text" id="helperDbName"></dd>
            <dt>Database User</dt>
            <dd><input type="text" id="helperUser"></dd>
            <dt>Database Password</dt>
            <dd><input type="text" id="helperPass"></dd>
            <dt class="jndiHelperItem">JNDI Resource Name</dt>
            <dd class="jndiHelperItem"><input type="text" id="helperJndiResouceName"></dd>
            <dt id="driverDt">Driver</dt>
            <dd id="driverDd">
                <ul class="driverPreset">
                    <li><a href="javascript:updateHelperContent('Postgres');">Postgres</a></li>
                    <li><a href="javascript:updateHelperContent('MariaDB');">MariaDB</a></li>
                    <li><a href="javascript:updateHelperContent('MySQL');">MySQL</a></li>
                </ul>
            </dd>
            <dt class="jndiHelperItem">JNDI Resource<c:import url="helpToolTip.jsp"><c:param name="topic" value="jndiresource"/></c:import></dt>
            <dd class="jndiHelperItem">
                <textarea id="jndiResource" rows="12"></textarea>
            </dt>
        </dl>
    </details>

    <dl id="URLDatabaseOptions" class="hideawayDatabaseOptions">
        <dt><strong>*</strong> <fmt:message key="databasesettings.embeddriver"/></dt>
        <dd>
            <form:input path="embedDriver"/>
            <c:import url="helpToolTip.jsp"><c:param name="topic" value="embeddriver"/></c:import>
        </dd>
        <dt><strong>*</strong> <fmt:message key="databasesettings.embedurl"/></dt>
        <dd><form:input path="embedUrl"/></dd>
        <dt><strong>*</strong> <fmt:message key="databasesettings.embedusername"/></dt>
        <dd><form:input path="embedUsername"/></dd>
        <dt><strong>*</strong> <fmt:message key="databasesettings.embedpassword"/></dt>
        <dd><form:input path="embedPassword"/></dd>
    </dl>

    <dl id="JNDIDatabaseOptions" class="hideawayDatabaseOptions single">
        <dt><strong>*</strong> <fmt:message key="databasesettings.jndiname"/></dt>
        <dd><form:input path="JNDIName"/><c:import url="helpToolTip.jsp"><c:param name="topic" value="jndiname"/></c:import></dd>
    </dl>

    <dl id="nonHOSTDatabaseOptions" class="hideawayDatabaseOptions">
        <dt><fmt:message key="databasesettings.mysqlvarcharmaxlength"/></dt>
        <dd>
            <form:input path="mysqlVarcharMaxlength"/>
            <c:import url="helpToolTip.jsp"><c:param name="topic" value="mysqlvarcharmaxlength"/></c:import>
        </dd>
        <dt><fmt:message key="databasesettings.usertablequote"/></dt>
        <dd>
            <form:input path="usertableQuote" htmlEscape="true"/>
            <c:import url="helpToolTip.jsp"><c:param name="topic" value="usertablequote"/></c:import>
        </dd>
    </dl>

    <div class="submits">
        <input type="submit" value="<fmt:message key='common.save'/>">
    </div>

</form:form>

</body>
</html>
