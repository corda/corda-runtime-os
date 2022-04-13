package net.corda.v5.ledger.contracts;

import net.corda.v5.crypto.SecureHash;
import net.corda.v5.ledger.contracts.TransactionResolutionException.UnknownParametersException;
import net.corda.v5.ledger.contracts.TransactionVerificationException.ContractRejection;
import net.corda.v5.ledger.contracts.TransactionVerificationException.ConstraintPropagationRejection;
import net.corda.v5.ledger.contracts.TransactionVerificationException.InvalidConstraintRejection;
import net.corda.v5.ledger.contracts.TransactionVerificationException.ContractConstraintRejection;
import net.corda.v5.ledger.contracts.TransactionVerificationException.MissingAttachmentRejection;
import net.corda.v5.ledger.contracts.TransactionVerificationException.ConflictingAttachmentsRejection;
import net.corda.v5.ledger.contracts.TransactionVerificationException.DuplicateAttachmentsRejection;
import net.corda.v5.ledger.contracts.TransactionVerificationException.ContractCreationError;
import net.corda.v5.ledger.contracts.TransactionVerificationException.NotaryChangeInWrongTransactionType;
import net.corda.v5.ledger.contracts.TransactionVerificationException.TransactionMissingEncumbranceException;
import net.corda.v5.ledger.contracts.TransactionVerificationException.Direction;
import net.corda.v5.ledger.contracts.TransactionVerificationException.TransactionDuplicateEncumbranceException;
import net.corda.v5.ledger.contracts.TransactionVerificationException.TransactionNonMatchingEncumbranceException;
import net.corda.v5.ledger.contracts.TransactionVerificationException.TransactionNotaryMismatchEncumbranceException;
import net.corda.v5.ledger.contracts.TransactionVerificationException.TransactionContractConflictException;
import net.corda.v5.ledger.contracts.TransactionVerificationException.TransactionRequiredContractUnspecifiedException;
import net.corda.v5.ledger.contracts.TransactionVerificationException.TransactionGroupParameterOrderingException;
import net.corda.v5.ledger.contracts.TransactionVerificationException.MissingGroupParametersException;
import net.corda.v5.ledger.contracts.TransactionVerificationException.BrokenTransactionException;
import net.corda.v5.ledger.contracts.TransactionVerificationException.InvalidAttachmentException;
import net.corda.v5.ledger.contracts.TransactionVerificationException.UnsupportedClassVersionError;
import net.corda.v5.ledger.contracts.TransactionVerificationException.UntrustedAttachmentsException;
import net.corda.v5.ledger.contracts.TransactionVerificationException.UnsupportedHashTypeException;
import net.corda.v5.ledger.identity.Party;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;


import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TransactionVerificationExceptionJavaApiTest {
    private final SecureHash secureHash =
            SecureHash.create("SHA-256:6A1687C143DF792A011A1E80670A4E4E0C25D0D87A39514409B1ABFC2043581A");
    private final String message = "message";
    private final Throwable cause = mock(Throwable.class);
    private final String contractClass = Contract.class.toString();

    @Nested
    public class TransactionResolutionExceptionJavaApiTest {
        private final TransactionResolutionException transactionResolutionException =
                new TransactionResolutionException(secureHash, message);

        @Test
        public void test_initialization() {
            final TransactionResolutionException transactionResolutionException1 =
                    new TransactionResolutionException(secureHash);

            Assertions.assertThat(transactionResolutionException1).isNotNull();
            Assertions.assertThat(transactionResolutionException).isNotNull();
            Assertions.assertThat(transactionResolutionException1).isNotEqualTo(transactionResolutionException);
        }

        @Test
        public void hash() {
            final SecureHash secureHash1 = transactionResolutionException.getHash();

            Assertions.assertThat(secureHash1).isNotNull();
        }

        @Test
        public void message() {
            final String message1 = transactionResolutionException.getMessage();

            Assertions.assertThat(message1).isEqualTo(message);
        }
    }

    @Nested
    public class UnknownParametersExceptionJavaApiTest {
        private final UnknownParametersException unknownParametersException =
                new UnknownParametersException(secureHash, secureHash);

        @Test
        public void txId() {
            Assertions.assertThat(unknownParametersException.getHash()).isEqualTo(secureHash);
        }

        @Test
        public void message() {
            Assertions.assertThat(unknownParametersException.getMessage()).isNotNull();
        }
    }

    @Nested
    public class AttachmentResolutionExceptionJavaApiTest {

        @Test
        public void hash() {
            final AttachmentResolutionException attachmentResolutionException =
                    new AttachmentResolutionException(secureHash);

            Assertions.assertThat(attachmentResolutionException.getHash()).isEqualTo(secureHash);
        }
    }

    @Nested
    public class BrokenAttachmentExceptionJavaApiTest {
        private final BrokenAttachmentException brokenAttachmentException =
                new BrokenAttachmentException(secureHash, message, cause);

        @Test
        public void attachmentId() {
            Assertions.assertThat(brokenAttachmentException.getAttachmentId()).isEqualTo(secureHash);
        }

        @Test
        public void message() {
            Assertions.assertThat(brokenAttachmentException.getMessage()).isNotNull();
        }

        @Test
        public void cause() {
            Assertions.assertThat(brokenAttachmentException.getCause()).isEqualTo(cause);
        }
    }


    @Nested
    public class TransactionVerificationExceptionAbstractJavaApiTest {
        private final TransactionVerificationException transactionVerificationException =
                mock(TransactionVerificationException.class);

        @Test
        public void message() {
            when(transactionVerificationException.getMessage()).thenReturn(message);

            Assertions.assertThat(transactionVerificationException.getMessage()).isEqualTo(message);
        }

        @Test
        public void cause() {
            when(transactionVerificationException.getCause()).thenReturn(cause);

            Assertions.assertThat(transactionVerificationException.getCause()).isEqualTo(cause);
        }
    }

    @Nested
    public class ContractRejectionJavaApiTest {
        private final ContractRejection contractRejection =
                new ContractRejection(secureHash, contractClass, cause, message);

        @Test
        public void txId() {
            Assertions.assertThat(contractRejection.getTxId()).isEqualTo(secureHash);
        }

        @Test
        public void contractClass() {
            Assertions.assertThat(contractRejection.getContractClass()).isEqualTo(contractClass);
        }

        @Test
        public void cause() {
            Assertions.assertThat(contractRejection.getCause()).isEqualTo(cause);
        }

        @Test
        public void message() {
            Assertions.assertThat(contractRejection.getMessage()).isNotNull();
        }
    }

    @Nested
    public class ConstraintPropagationRejectionJavaApiTest {
        private final CPKConstraint inputConstraint = mock(CPKConstraint.class);
        private final CPKConstraint outputConstraint = mock(CPKConstraint.class);
        private final ConstraintPropagationRejection constraintPropagationRejection =
                new ConstraintPropagationRejection(secureHash, contractClass, inputConstraint, outputConstraint);

        @Test
        public void contractClass() {
            Assertions.assertThat(constraintPropagationRejection.getContractClass()).isNotNull();
        }
    }

    @Nested
    public class ContractConstraintRejectionJavaApiTest {
        private final ContractConstraintRejection contractConstraintRejection =
                new ContractConstraintRejection(secureHash, contractClass);

        @Test
        public void contractClass() {
            Assertions.assertThat(contractConstraintRejection.getContractClass()).isEqualTo(contractClass);
        }
    }

    @Nested
    public class InvalidConstraintRejectionJavaApiTest {
        private final String reason = "reason";
        private final InvalidConstraintRejection invalidConstraintRejection =
                new InvalidConstraintRejection(secureHash, contractClass, reason);

        @Test
        public void reason() {
            Assertions.assertThat(invalidConstraintRejection.getReason()).isEqualTo(reason);
        }
    }

    @Nested
    public class MissingAttachmentRejectionJavaApiTest {
        private final MissingAttachmentRejection missingAttachmentRejection =
                new MissingAttachmentRejection(secureHash, contractClass);

        @Test
        public void txId() {
            Assertions.assertThat(missingAttachmentRejection.getTxId()).isEqualTo(secureHash);
        }

        @Test
        public void contractClass() {
            Assertions.assertThat(missingAttachmentRejection.getContractClass()).isEqualTo(contractClass);
        }
    }

    @Nested
    public class ConflictingAttachmentsRejectionJavaApiTest {
        private final ConflictingAttachmentsRejection conflictingAttachmentsRejection =
                new ConflictingAttachmentsRejection(secureHash, contractClass);

        @Test
        public void txId() {
            Assertions.assertThat(conflictingAttachmentsRejection.getTxId()).isEqualTo(secureHash);
        }

        @Test
        public void contractClass() {
            Assertions.assertThat(conflictingAttachmentsRejection.getContractClass()).isEqualTo(contractClass);
        }
    }

    @Nested
    public class DuplicateAttachmentsRejectionJavaApiTest {
        private final Attachment attachment = mock(Attachment.class);
        private final DuplicateAttachmentsRejection duplicateAttachmentsRejection =
                new DuplicateAttachmentsRejection(secureHash, attachment);

        @Test
        public void txId() {
            Assertions.assertThat(duplicateAttachmentsRejection.getTxId()).isEqualTo(secureHash);
        }

        @Test
        public void attachmentId() {
            Assertions.assertThat(duplicateAttachmentsRejection.getAttachmentId()).isEqualTo(attachment);
        }
    }

    @Nested
    public class ContractCreationErrorJavaApiTest {
        private final ContractCreationError contractCreationError =
                new ContractCreationError(secureHash, contractClass, cause);

        @Test
        public void txId() {
            Assertions.assertThat(contractCreationError.getTxId()).isEqualTo(secureHash);
        }

        @Test
        public void contractClass() {
            Assertions.assertThat(contractCreationError.getContractClass()).isEqualTo(contractClass);
        }

        @Test
        public void cause() {
            Assertions.assertThat(contractCreationError.getCause()).isEqualTo(cause);
        }
    }

    @Nested
    public class NotaryChangeInWrongTransactionTypeJavaApiTest {
        private final Party txNotary = mock(Party.class);
        private final Party outputNotary = mock(Party.class);
        private final NotaryChangeInWrongTransactionType notaryChangeInWrongTransactionType =
                new NotaryChangeInWrongTransactionType(secureHash, txNotary, outputNotary);

        @Test
        public void txId() {
            Assertions.assertThat(notaryChangeInWrongTransactionType.getTxId()).isEqualTo(secureHash);
        }

        @Test
        public void txNotary() {
            Assertions.assertThat(notaryChangeInWrongTransactionType.getTxNotary()).isEqualTo(txNotary);
        }

        @Test
        public void outputNotary() {
            Assertions.assertThat(notaryChangeInWrongTransactionType.getOutputNotary()).isEqualTo(outputNotary);
        }
    }

    @Nested
    public class TransactionMissingEncumbranceExceptionJavaApiTest {
        private final TransactionMissingEncumbranceException transactionMissingEncumbranceException =
                new TransactionMissingEncumbranceException(secureHash, 5, Direction.INPUT);

        @Test
        public void txId() {
            Assertions.assertThat(transactionMissingEncumbranceException.getTxId()).isEqualTo(secureHash);
        }

        @Test
        public void missing() {
            Assertions.assertThat(transactionMissingEncumbranceException.getMissing()).isEqualTo(5);
        }

        @Test
        public void inOut() {
            Assertions.assertThat(transactionMissingEncumbranceException.getInOut()).isNotNull();
        }
    }

    @Nested
    public class TransactionDuplicateEncumbranceExceptionJavaApiTest {
        private final TransactionDuplicateEncumbranceException transactionDuplicateEncumbranceException =
                new TransactionDuplicateEncumbranceException(secureHash, message);

        @Test
        public void txId() {
            Assertions.assertThat(transactionDuplicateEncumbranceException.getTxId()).isEqualTo(secureHash);
        }

        @Test
        public void message() {
            Assertions.assertThat(transactionDuplicateEncumbranceException.getMessage()).isNotNull();
        }
    }

    @Nested
    public class TransactionNonMatchingEncumbranceExceptionJavaApiTest {
        private final List<Integer> nonMatching = List.of(5);
        private final TransactionNonMatchingEncumbranceException transactionNonMatchingEncumbranceException =
                new TransactionNonMatchingEncumbranceException(secureHash, nonMatching);

        @Test
        public void txId() {
            Assertions.assertThat(transactionNonMatchingEncumbranceException.getTxId()).isEqualTo(secureHash);
        }
    }

    @Nested
    public class TransactionNotaryMismatchEncumbranceExceptionJavaApiTest {
        private final TransactionNotaryMismatchEncumbranceException transactionNonMatchingEncumbranceException =
                new TransactionNotaryMismatchEncumbranceException(secureHash, message);

        @Test
        public void txId() {
            Assertions.assertThat(transactionNonMatchingEncumbranceException.getTxId()).isEqualTo(secureHash);
        }

        @Test
        public void message() {
            Assertions.assertThat(transactionNonMatchingEncumbranceException.getMessage()).isNotNull();
        }
    }

    @Nested
    public class TransactionContractConflictExceptionJavaApiTest {
        private final TransactionContractConflictException transactionNonMatchingEncumbranceException =
                new TransactionContractConflictException(secureHash, message);

        @Test
        public void txId() {
            Assertions.assertThat(transactionNonMatchingEncumbranceException.getTxId()).isEqualTo(secureHash);
        }

        @Test
        public void message() {
            Assertions.assertThat(transactionNonMatchingEncumbranceException.getMessage()).isNotNull();
        }
    }

    @Nested
    public class TransactionRequiredContractUnspecifiedExceptionJavaApiTest {
        private final TransactionRequiredContractUnspecifiedException transactionNonMatchingEncumbranceException =
                new TransactionRequiredContractUnspecifiedException(secureHash, message);

        @Test
        public void txId() {
            Assertions.assertThat(transactionNonMatchingEncumbranceException.getTxId()).isEqualTo(secureHash);
        }

        @Test
        public void message() {
            Assertions.assertThat(transactionNonMatchingEncumbranceException.getMessage()).isNotNull();
        }
    }

    @Nested
    public class TransactionGroupParameterOrderingExceptionJavaApiTest {
        private final TransactionGroupParameterOrderingException transactionNonMatchingEncumbranceException =
                new TransactionGroupParameterOrderingException(secureHash, message);

        @Test
        public void txId() {
            Assertions.assertThat(transactionNonMatchingEncumbranceException.getTxId()).isEqualTo(secureHash);
        }

        @Test
        public void message() {
            Assertions.assertThat(transactionNonMatchingEncumbranceException.getMessage()).isNotNull();
        }
    }

    @Nested
    public class MissingGroupParametersExceptionJavaApiTest {
        private final MissingGroupParametersException transactionNonMatchingEncumbranceException =
                new MissingGroupParametersException(secureHash, message);

        @Test
        public void txId() {
            Assertions.assertThat(transactionNonMatchingEncumbranceException.getTxId()).isEqualTo(secureHash);
        }

        @Test
        public void message() {
            Assertions.assertThat(transactionNonMatchingEncumbranceException.getMessage()).isNotNull();
        }
    }

    @Nested
    public class BrokenTransactionExceptionJavaApiTest {
        private final BrokenTransactionException transactionNonMatchingEncumbranceException =
                new BrokenTransactionException(secureHash, message);

        @Test
        public void txId() {
            Assertions.assertThat(transactionNonMatchingEncumbranceException.getTxId()).isEqualTo(secureHash);
        }

        @Test
        public void message() {
            Assertions.assertThat(transactionNonMatchingEncumbranceException.getMessage()).isNotNull();
        }
    }

    @Nested
    public class InvalidAttachmentExceptionJavaApiTest {
        private final InvalidAttachmentException invalidAttachmentException =
                new InvalidAttachmentException(secureHash, secureHash);

        @Test
        public void txId() {
            Assertions.assertThat(invalidAttachmentException.getTxId()).isEqualTo(secureHash);
        }

        @Test
        public void attachmentHash() {
            Assertions.assertThat(invalidAttachmentException.getAttachmentHash()).isEqualTo(secureHash);
        }
    }

    @Nested
    public class UnsupportedClassVersionErrorJavaApiTest {
        private final UnsupportedClassVersionError unsupportedClassVersionError =
                new UnsupportedClassVersionError(secureHash, message, cause);

        @Test
        public void txId() {
            Assertions.assertThat(unsupportedClassVersionError.getTxId()).isEqualTo(secureHash);
        }

        @Test
        public void message() {
            Assertions.assertThat(unsupportedClassVersionError.getMessage()).isNotNull();
        }

        @Test
        public void clause() {
            Assertions.assertThat(unsupportedClassVersionError.getCause()).isEqualTo(cause);
        }
    }

    @Nested
    public class UnsupportedHashTypeExceptionJavaApiTest {
        @Test
        public void txId() {
            final UnsupportedHashTypeException unsupportedHashTypeException = new UnsupportedHashTypeException(secureHash);
            Assertions.assertThat(unsupportedHashTypeException.getTxId()).isEqualTo(secureHash);
        }
    }

    @Nested
    public class UntrustedAttachmentsExceptionJavaApiTest {
        private final List<SecureHash> ids = List.of(secureHash);
        private final UntrustedAttachmentsException untrustedAttachmentsException =
                new UntrustedAttachmentsException(secureHash, ids);

        @Test
        public void txId() {
            Assertions.assertThat(untrustedAttachmentsException.getTxId()).isEqualTo(secureHash);
        }

        @Test
        public void ids() {
            Assertions.assertThat(untrustedAttachmentsException.getIds()).isEqualTo(ids);
        }
    }
}
