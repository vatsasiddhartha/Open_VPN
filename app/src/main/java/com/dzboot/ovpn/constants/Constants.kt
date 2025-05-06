package com.dzboot.ovpn.constants

import org.lsposed.lsparanoid.Obfuscate


@Obfuscate
object Constants {

    fun isNotSet() =
        CLIENT_CERT.isBlank() || CLIENT_CERT.equals("{client_key}", true) ||
                CA_CERT.isBlank() || CA_CERT.equals("{ca_cert}", true) ||
                CLIENT_CERT.isBlank() || CLIENT_CERT.equals("{client_cert}", true) ||
                TA.isBlank() || TA.equals("{ta}", true)

    val CLIENT_KEY = """-----BEGIN PRIVATE KEY-----
MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQDY6hMGSZbkZZKE
9gGzQz28bGDP8y55fmH7b27EgaJYfeBWGhArG5DedkPlPoQ/FGsI4ed4WbaNP1Eo
nOC7OkIBuPBNJPE46sIuPjat7a3v1yAoLkSZVNkVLppfxGWvH1cdnB9s/djgTTw1
XWqyvPEDOCpG6Xul7+rdc9etQxYgpgKRW2qmLEQZqroG1dqgIOSyU7endO2Y56LC
SC/Xwg16A7PoeSh7K6gXr0Ipgw6Nh5g/XrdYaShq/kvKqKMJuA88ZzClrhSz1ViB
v4hGItiwjccENAxVXUSXuAOEU2jWgA+r1dIrVTdsV22nPq0ElvuabCph1nyykdpX
wESfvwkpAgMBAAECggEADto6OsDmXCsIn7eLhsIuKOfKhOVLvkW/1kb7RekdcWM5
wujZHhEut84lGH48cRLywEKIRlFC7W7TiGJzhdu3l3rEY3ffLNdFkVkxBnLtk8Gp
F0SRwxD6+0G8EB4jG0exVUWdJj9HGWwDGSFVdA8kDo/K30ekmohauSvx2DsteZyG
wQSFydKMPzSfwbvRgtdA2hEqJ+7+XkHzE7XY3Ad5U1pTc1TKV62WfKX6PIwXKqpr
w/ok4D1B8ZMndeNg1yYvhvPzPGGOOfcdJHLnIbm8kKK/pCags/3CMoDoZmZ1aKY2
EcF3/EC8NzDtmbRtmGJB0NXCDOhIkbTdnHOLRtMerQKBgQDzDrTFJ49kfs2O5mWt
m9XQ6VdsVl5Qw5BqiwsUO+k5S6nuTuSZsV2hU8E1P/EUervp0cO5OIpyYSTizvVC
Q/uFv69xzbJrgTjNc4edXPmjnSiuJFsGmC+EjOHXvFSjy9GpRXgrMrikP/sbQuGM
WHjdN9MiwGYXXhFTvg7bOWJHTQKBgQDkdv4X/q1C69jF6+M22nFTpssnPgaPX+cW
C05mJ4LRT1vZ4ZSnBrLM183EgcytTg8mWFABk+wu3y+L4omLMiLwO6QdZFzRWCYi
EFm0ZzjBZfut/sRTsY/htucjkB1lhbMF0GSUUWPgFWBNqLEfsbi5B1SS0IcZAS/C
qLT4D8hzTQKBgQDrrVfVT+23eUjihj8TXuatwoS1lO2xDF/tH1On1AvqNZbZb4A0
EwqB0s+mTZD61aN+LprE77E3BUEZ96lTDs5Fuz9obxbRLSSh+qMGEFAzuMDoUnI8
67YvpdebM48yoAB1TNofsPfes/dNUMWZK3ROtMGykbKsd9b/R2vqYDMOhQKBgB4T
3FfkW4MX8a385CjwkULmDpDYBZ0SjyXDz0p7oSlVPU5t+FgU9a0qLBjWzc9zG2N8
9OuAQfPZxjSwWlNNAnSGJZEJCX82D68kX+r0O/CaMIwzoj0yfzLEFaIE8xnOhEcb
rGIqZO+3YLpYnxkBGMSjRmsdsOuF0HfcJhcSXN1xAoGBANnGZK6lA+QeUlwEyXDL
e6iwmpLsFo4GJOv4KRt53WBmLGV5xZkM7Cd2CiYJ8T1NOeAy47d0REpbCFFR/f/F
XVIOPmeivyHp0Dm8c+t/cNL/XYM+/eKKgUHmgTloavzwCkJdvMHjDMsRK9v9kbz/
ElDu2lzRmAUkLqqHWo8DFMMX
-----END PRIVATE KEY-----"""
    val CA_CERT = """-----BEGIN CERTIFICATE-----
MIIDNjCCAh6gAwIBAgIURJldPzduRuRrtwqBiDwdXxQ93/EwDQYJKoZIhvcNAQEL
BQAwDzENMAsGA1UEAwwEb3ZwbjAeFw0yNDA0MTAxOTQ2MTBaFw0zNDA0MDgxOTQ2
MTBaMA8xDTALBgNVBAMMBG92cG4wggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEK
AoIBAQCdw4u/lS8cROOhuG860Ih0hK6/hpnoohU1wFV9hLauGGKEOf5vZ3FopaOW
+Y8JfP3wKSEQSpnzVvl578t7DOTxONMjHL5jhqwraBX98lcXyM2pg4ffGy0b9i5G
z8q5tEdPnm4FLzL09JhgkIGNdlobvTkleODGnsX5f9wVVYb/NPW+18wmu91ZdW7D
eKwH/GKS3pzNIZm+M6bYBwmXen3W9f9M4HCvHYNLALcVJXhaf36zrOK21IiStgUA
90TJjrBpanvNpAitUj6q4DTFiHXG4CW09DVL/aOsftO74PjbufwDxae6OeDoY5VG
5b/jgt7ssKVqzexsICyFyP0pbXuXAgMBAAGjgYkwgYYwDAYDVR0TBAUwAwEB/zAd
BgNVHQ4EFgQUgyiwtlT4qKiJ7i1GUNbulhSF/1swSgYDVR0jBEMwQYAUgyiwtlT4
qKiJ7i1GUNbulhSF/1uhE6QRMA8xDTALBgNVBAMMBG92cG6CFESZXT83bkbka7cK
gYg8HV8UPd/xMAsGA1UdDwQEAwIBBjANBgkqhkiG9w0BAQsFAAOCAQEARY8id4/n
r649d6D4mxWPkCrWjzJds9Q/VrrFUNmifeVLYPoYd8LPw5v+NEBZgorl49wTktwu
Fo1Uf/oSMsP6f3GFmUOZDBn2f7Q2RIVqbD6LwjzKV/fDkWrSmC2gGmButPG/MDGJ
TbBVHoiQIHle9MC6Qvw46GfTHF4vSxiZgQ6ldgviYEwRkUBQs3+33/qrTe2pwVza
s5Jlu184Le/6ZszHdMhti4ciU8PfcaJGCZDNFLvgjw5y7nER+TTAsQtNYRKlCxIe
eu4PZb+ket9zXeDyibFSkLBYuRIHagilLVSWiY77n9HdxN0+/uIP+ey4M4N80gDL
MSGC6gt78+0sEA==
-----END CERTIFICATE-----"""
    val CLIENT_CERT = """Certificate:
    Data:
        Version: 3 (0x2)
        Serial Number:
            92:e5:e3:77:b4:04:ea:73:72:be:80:7b:ce:9f:0d:38
        Signature Algorithm: sha256WithRSAEncryption
        Issuer: CN=ovpn
        Validity
            Not Before: Apr 10 19:46:10 2024 GMT
            Not After : Jul 14 19:46:10 2026 GMT
        Subject: CN=ovpn
        Subject Public Key Info:
            Public Key Algorithm: rsaEncryption
                Public-Key: (2048 bit)
                Modulus:
                    00:d8:ea:13:06:49:96:e4:65:92:84:f6:01:b3:43:
                    3d:bc:6c:60:cf:f3:2e:79:7e:61:fb:6f:6e:c4:81:
                    a2:58:7d:e0:56:1a:10:2b:1b:90:de:76:43:e5:3e:
                    84:3f:14:6b:08:e1:e7:78:59:b6:8d:3f:51:28:9c:
                    e0:bb:3a:42:01:b8:f0:4d:24:f1:38:ea:c2:2e:3e:
                    36:ad:ed:ad:ef:d7:20:28:2e:44:99:54:d9:15:2e:
                    9a:5f:c4:65:af:1f:57:1d:9c:1f:6c:fd:d8:e0:4d:
                    3c:35:5d:6a:b2:bc:f1:03:38:2a:46:e9:7b:a5:ef:
                    ea:dd:73:d7:ad:43:16:20:a6:02:91:5b:6a:a6:2c:
                    44:19:aa:ba:06:d5:da:a0:20:e4:b2:53:b7:a7:74:
                    ed:98:e7:a2:c2:48:2f:d7:c2:0d:7a:03:b3:e8:79:
                    28:7b:2b:a8:17:af:42:29:83:0e:8d:87:98:3f:5e:
                    b7:58:69:28:6a:fe:4b:ca:a8:a3:09:b8:0f:3c:67:
                    30:a5:ae:14:b3:d5:58:81:bf:88:46:22:d8:b0:8d:
                    c7:04:34:0c:55:5d:44:97:b8:03:84:53:68:d6:80:
                    0f:ab:d5:d2:2b:55:37:6c:57:6d:a7:3e:ad:04:96:
                    fb:9a:6c:2a:61:d6:7c:b2:91:da:57:c0:44:9f:bf:
                    09:29
                Exponent: 65537 (0x10001)
        X509v3 extensions:
            X509v3 Basic Constraints: 
                CA:FALSE
            X509v3 Subject Key Identifier: 
                27:12:52:AF:79:91:10:1B:07:3B:1E:B2:06:5B:E3:FE:D8:8C:F4:50
            X509v3 Authority Key Identifier: 
                keyid:83:28:B0:B6:54:F8:A8:A8:89:EE:2D:46:50:D6:EE:96:14:85:FF:5B
                DirName:/CN=ovpn
                serial:44:99:5D:3F:37:6E:46:E4:6B:B7:0A:81:88:3C:1D:5F:14:3D:DF:F1
            X509v3 Extended Key Usage: 
                TLS Web Client Authentication
            X509v3 Key Usage: 
                Digital Signature
    Signature Algorithm: sha256WithRSAEncryption
    Signature Value:
        36:0c:23:9d:be:71:64:a7:e0:f0:41:03:13:60:94:65:11:64:
        f9:38:b6:32:6a:56:6b:dc:dc:8c:b9:99:ab:c4:0f:cf:5e:54:
        1c:d3:8c:dd:55:44:91:1b:49:6d:9c:b8:68:19:44:31:6f:43:
        8f:b5:e4:ca:d1:30:36:1d:c8:2f:a1:99:5c:e9:75:b4:6b:df:
        63:9e:d0:8f:a4:34:af:68:98:d2:4d:05:0c:f2:a3:07:6d:99:
        d1:b7:5d:ec:90:06:37:0c:b4:7a:5f:af:8b:aa:0b:bf:db:36:
        8a:1b:18:85:d7:e1:e7:3d:e3:e8:f5:9a:f8:e4:d0:12:30:1d:
        20:18:f7:3c:63:eb:37:26:12:0a:bf:16:b1:6b:6f:d9:3e:af:
        76:1a:12:c4:be:6c:84:c8:8e:67:6f:f1:c4:e2:b0:13:07:39:
        ce:df:61:9d:7e:71:d9:bc:68:fd:96:5d:a1:4c:31:66:f4:18:
        ca:59:f0:ea:5f:cf:02:d2:7e:69:3f:11:49:f2:c2:ce:5c:9e:
        5d:58:11:6c:b6:7d:fa:2b:5f:75:17:3a:4a:36:9f:14:36:80:
        1b:e2:a4:54:9c:2a:aa:7c:f9:40:36:aa:43:c2:1f:cb:d5:fa:
        ed:12:a5:bf:4c:7a:ae:70:bd:fc:a3:ec:05:c3:15:4a:49:cb:
        26:c2:52:a0
-----BEGIN CERTIFICATE-----
MIIDRTCCAi2gAwIBAgIRAJLl43e0BOpzcr6Ae86fDTgwDQYJKoZIhvcNAQELBQAw
DzENMAsGA1UEAwwEb3ZwbjAeFw0yNDA0MTAxOTQ2MTBaFw0yNjA3MTQxOTQ2MTBa
MA8xDTALBgNVBAMMBG92cG4wggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIB
AQDY6hMGSZbkZZKE9gGzQz28bGDP8y55fmH7b27EgaJYfeBWGhArG5DedkPlPoQ/
FGsI4ed4WbaNP1EonOC7OkIBuPBNJPE46sIuPjat7a3v1yAoLkSZVNkVLppfxGWv
H1cdnB9s/djgTTw1XWqyvPEDOCpG6Xul7+rdc9etQxYgpgKRW2qmLEQZqroG1dqg
IOSyU7endO2Y56LCSC/Xwg16A7PoeSh7K6gXr0Ipgw6Nh5g/XrdYaShq/kvKqKMJ
uA88ZzClrhSz1ViBv4hGItiwjccENAxVXUSXuAOEU2jWgA+r1dIrVTdsV22nPq0E
lvuabCph1nyykdpXwESfvwkpAgMBAAGjgZswgZgwCQYDVR0TBAIwADAdBgNVHQ4E
FgQUJxJSr3mREBsHOx6yBlvj/tiM9FAwSgYDVR0jBEMwQYAUgyiwtlT4qKiJ7i1G
UNbulhSF/1uhE6QRMA8xDTALBgNVBAMMBG92cG6CFESZXT83bkbka7cKgYg8HV8U
Pd/xMBMGA1UdJQQMMAoGCCsGAQUFBwMCMAsGA1UdDwQEAwIHgDANBgkqhkiG9w0B
AQsFAAOCAQEANgwjnb5xZKfg8EEDE2CUZRFk+Ti2MmpWa9zcjLmZq8QPz15UHNOM
3VVEkRtJbZy4aBlEMW9Dj7XkytEwNh3IL6GZXOl1tGvfY57Qj6Q0r2iY0k0FDPKj
B22Z0bdd7JAGNwy0el+vi6oLv9s2ihsYhdfh5z3j6PWa+OTQEjAdIBj3PGPrNyYS
Cr8WsWtv2T6vdhoSxL5shMiOZ2/xxOKwEwc5zt9hnX5x2bxo/ZZdoUwxZvQYylnw
6l/PAtJ+aT8RSfLCzlyeXVgRbLZ9+itfdRc6SjafFDaAG+KkVJwqqnz5QDaqQ8If
y9X67RKlv0x6rnC9/KPsBcMVSknLJsJSoA==
-----END CERTIFICATE-----"""
    val TA = """#
# 2048 bit OpenVPN static key
#
-----BEGIN OpenVPN Static key V1-----
f783bbffb7842aaef57c1804691e6682
aeebca61555db03aa398db5993c10254
32976641ef2ed5c5e3e97a49dca29440
f583f95034dd28ca1b0de2975493971a
c9eb447e58f737abc8ab1f575f3cc094
c06c55e0840d811b610014998145460c
cb76588cec6fbf5f53c616a5ab0a7265
8014d1d6f487e1b92e3cd621a318041d
8280558b0c6f203cc29e9d7050be040b
5a33347144fe840a73b895f967108895
69119137bc6d320ef50a1d472a245ea2
fc507f926fbacd932cbfc74c13136ddc
48a644cce0c0ad17670e2020e58ede30
7b8dbfcade3efbad75c2af3977a57de8
a031644718d76de9cc7f9a5169deed3f
63484a84cc4a62f513444a997f9f0242
-----END OpenVPN Static key V1-----"""

    val PUBLIC_KEY = """-----BEGIN PUBLIC KEY-----
MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDA724x+57QtjuLEpx9mojVrDm3
GEhNwJsgQHvDOQF0bU8qZqzYEvcATpfx05bi0Pb5wDM+ekrLgW4Dfx5cBgFIPYjV
PIv6ti0aPkTo/yDR6zf3aX7pcX0rbKQ9Cz6TPwKrmIM1KUCAaX9mL7kg0OtAc48h
2KQw+kA/ulrO57e24QIDAQAB
-----END PUBLIC KEY-----"""
    val API_KEY = """bHVZeJLWUQEasmyFNmiR"""
	
}
