#### README FILE ####

#### NAME - USERNAME ####

Nadav Zaltsman - nadav.zaltsman
Alon Greenfield - alon.greenfield

##################################

#### FILES EXPLANATIONS ####

SinkholeServer - entry point for the program, checks blacklist enabled/disabled

UDPServer - Acts as component connecting between the client (the requests sender) and the DNS program

DNSServer- backend component to manage the logic actions for requests and responses, connect between the UDPServer and
the UDPClient.
transfer requests and checks iteratively for responses.
for blacklisted or un-existing domains - reply with status:NXDOMAIN

UDPClient - the component that is used to query the dns servers (root and subs), send a request and transfer the response
to the DNSServer (backend)

##################################

#### END OF README ####