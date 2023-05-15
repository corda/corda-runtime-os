The AWS KMS Java package contains JCE Provider classes (https://github.com/aws-samples/aws-kms-jce).
They are useful for AWS KMS integration into `corda-cli` tool as they provide important interaction with KMS which
cannot be achieved via AWS CLI.
Original classes contain Lombok dependency, which was removed.

Examples on how to use JCE Provider can be found in the original repo https://github.com/aws-samples/aws-kms-jce.
