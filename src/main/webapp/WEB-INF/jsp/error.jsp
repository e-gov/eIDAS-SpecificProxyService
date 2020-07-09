<!DOCTYPE html>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<fmt:setBundle basename="eu.eidas.specific.proxyservice.package" var="i18n_eng"/>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="e" uri="https://www.owasp.org/index.php/OWASP_Java_Encoder_Project" %>
<html>
<head>
    <meta charset="utf-8">
    <meta name="description" content="Eesti autentimisteenus">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <link rel="icon" href="resource/error/favicon/favicon.ico" type="image/x-icon">
    <link rel="stylesheet" href="resource/error/css/main.min.css">
    <title>Eesti autentimisteenus</title>
</head>
<body>
<div class="c-layout">
    <div class="c-layout--full">
        <div class="c-header-bar">
            <div class="container">
                <div class="d-flex justify-content-between align-items-center">
                    <p id="subtitle">Secure authentication in e-Services of EU member states</p>
                </div>
            </div>
        </div>
        <header class="c-header" role="banner">
            <div class="container">
                <h1 class="c-header__logo">
                    <a class="c-header__logo-link" href="#">
                        <img id="eeidp-logo-et" src="resource/error/assets/eeidp-logo-et.png" alt="eeidp">
                        <span class="sr-only">Eesti autentimisteenus</span>
                    </a>
                </h1>
            </div>
        </header>
        <div class="container">
            <div class="alert alert-error" role="alert">
                <p id="error"><strong><c:out value="${error}"/></strong></p>
                <p id="message"><c:out value="${message}"/></p>
                <p id="errors"><i><c:out value="${errors}"/></i></p>
                <p id="incidentNumber"><strong>Eidas Proxyservice incident number: <c:out value="${incidentNumber}"/></strong></p>
            </div>
        </div>
    </div>
    <div class="c-footer">
        <div class="container">
            <div class="c-footer__row">
                <div class="c-footer__col">
                    <div class="c-footer__block">
                        <div class="c-footer__block-logo">
                            <img id="cef-logo-en" src="resource/error/assets/cef-logo-en.svg" alt="cef">
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </div>
</div>
</body>
</html>
