# nuxeo-labs-generic-service-call

This is Work In Progress. Do not use "as is".

We'll explain later what it is supposed to do :-)

<hr>

This plugin calls a service and returns the result. Caller is in charge of using the correct HTTP verb (get, post, put, ...) and setting the correct payload when needed.


> [!IMPORTANT]
> We use "generic" in the name as it does not call a hard coded service, but at first implementation, it does have a lot of limitation, especially in terms of authentication: First implementation handles a bearer token, calculated by a service to which we pass a clientId and clientSecret.
>


