# Public Amazon certificate authorities

Downloaded 17 October 2026 from official sources:

- `aws-rds-global-bundle.pem`: https://truststore.pki.rds.amazonaws.com/global/global-bundle.pem
- `amazon-root-ca-1.pem`: https://www.amazontrust.com/repository/AmazonRootCA1.pem
- `aws-postgres-ca-bundle.pem`: concatenates those unmodified PEM files for PostgreSQL clients.

The supplied endpoint presented a chain to Amazon Root CA 1 rather than a region-specific RDS CA. Amazon Root CA 1 was checked against Amazon Trust Services' published self-signed certificate SHA-256 fingerprint:

`8ecde6884f3d87b1125ba31ac3fcb13d7016de7f57cc904fe1cb97c6ae98196e`

Repository and certificate attribution: https://www.amazontrust.com/repository/
Amazon Trust Services certificate files are provided under the Creative Commons Attribution-NoDerivatives 4.0 International licence referenced there. Individual certificate contents are unchanged.

The combined bundle verified the supplied server certificate and hostname. Verification was not disabled. Refresh trust material from the official sources when required; these files contain public CA certificates, not private keys or credentials.
