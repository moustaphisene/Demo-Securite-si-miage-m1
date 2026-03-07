<#import "/spring.ftl" as spring />
<#assign xhtmlCompliant = true in spring>
<!DOCTYPE html>
<html>
<head>
    <title>Planets Page</title>
</head>
<body>

<header>
    <a href="/">Return Home</a>
    <a href="/logout" id="logout">Logout</a>
	
</header>

<h1>Planets Page</h1>
<p>User ${principal.name} made this request.</p>

<h2>Planets</h2>
<ul>
    <#list planets as planet>
        <li>${planet}</li>
    </#list>
</ul>

</body>
</html>
