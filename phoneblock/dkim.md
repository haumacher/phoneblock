## TXT record pb._domainkey.phoneblock.net

```
v=DKIM1;g=*;k=rsa;p=MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQC5Vhu5
H/G8SDS/cPa3/iFIp62nC6fTWf56EjuRAj4nlSO3jvaDXz3FJautc1bJ0s4mdjEc
VF63VHnvH+qRjwfeam5BsOJuBciOvKK5wJHZpwCwdDviQdRk7vRSU8A1M8VTKSge
uyZZsNeE5e2S1PxZyO0wQTCCzqKtNf9/9FngrQIDAQAB;s=email;t=s
```

## Generate public/private key pair

```
openssl pkcs8 -topk8 -nocrypt -in dkim.pem -outform der -out dkim.der
```

## Convert to key compatible with Java

```
openssl pkcs8 -topk8 -nocrypt -in dkim.pem -outform der -out dkim.der
```

## Extract public key

```
openssl rsa -in dkim.pem -pubout
```
