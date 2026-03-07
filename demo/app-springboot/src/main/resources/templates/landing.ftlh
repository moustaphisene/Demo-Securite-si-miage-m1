<#import "/spring.ftl" as spring />
<#assign xhtmlCompliant = true in spring>
<!DOCTYPE html>
<html>
<head>
    <title>Landing Page</title>
</head>
<body>
<h1>Home Page</h1>
<header>
    <a href="/about">About page (not authenticated)</a>
    <br><br><br>
    <a href="/planets">List planets (need authenticated)</a>

    <br><br><br>
    <a href="/logout">Logout</a>
</header>


</body>
</html>
