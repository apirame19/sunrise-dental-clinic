package lk.sunrisedental.service;

import lk.sunrisedental.dao.DentistDAO;
import lk.sunrisedental.dao.PatientDAO;
import lk.sunrisedental.dao.TransactionManager;
import lk.sunrisedental.dao.UserDAO;
import lk.sunrisedental.exception.ValidationException;
import lk.sunrisedental.model.Dentist;
import lk.sunrisedental.model.Patient;
import lk.sunrisedental.model.Role;
import lk.sunrisedental.model.User;
import lk.sunrisedental.security.PasswordHasher;
import lk.sunrisedental.validation.ValidationResult;

import java.util.Arrays;
import java.util.regex.Pattern;

/**
 * Self-registration for members of the public and for dentists.
 *
 * <p><strong>The role is never taken from the request.</strong> There is no parameter, anywhere in
 * this class, that influences which role an account receives. {@link #registerPatient} always
 * assigns {@link Role#PATIENT} and {@link #registerDentist} always assigns {@link Role#DENTIST},
 * both as compile-time constants. This is the single most important property here: an attacker who
 * appends {@code &role=ADMIN} to either form changes nothing, because there is no code path that
 * would read it. {@link Role#ADMIN} is not reachable from this class at all - the seeded
 * administrator is the only administrator, and it is created by {@code seed.sql}, never here.</p>
 *
 * <p><strong>A dentist registers inactive.</strong> A clinical login can read every patient's
 * record and the whole appointment book, so allowing an anonymous stranger to mint one and use it
 * immediately would hand the clinic's data to whoever asked first. The account is stored with
 * {@code is_active = 0} and an administrator activates it from the existing staff-accounts screen.
 * No new mechanism is needed to enforce the wait: {@code AuthenticationService} already refuses an
 * inactive account at sign-in. A patient, by contrast, registers active - a patient login can see
 * only its own appointments, so there is nothing to gate.</p>
 *
 * <p>Each registration writes two rows - the login and the {@code patients} or {@code dentists}
 * record it is bound to - inside one transaction. Half of that pair is useless: an account with no
 * record can sign in and be shown nothing, and a record with no account is an orphan nobody
 * maintains.</p>
 *
 * <p>A plain-text password never leaves this class. It arrives as a {@code char[]}, goes straight
 * to {@link PasswordHasher}, and only the derived hash, salt and iteration count reach the DAO.
 * Nothing here logs, returns or stores a credential, and no validation message ever quotes one.</p>
 */
public class RegistrationService {

    /** Letters, digits, dots and underscores; a username also appears in the audit trail. */
    private static final Pattern USERNAME = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_.]{2,49}$");

    /** Same floor the staff-account screen applies, so one rule governs every password. */
    private static final int MIN_PASSWORD_LENGTH = 10;

    private static final Pattern CONTACT_NUMBER = Pattern.compile("^[0-9+\\-\\s()]{9,20}$");

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}$");

    private final UserDAO userDAO;
    private final PatientDAO patientDAO;
    private final DentistDAO dentistDAO;
    private final TransactionManager transactionManager;

    public RegistrationService(UserDAO userDAO, PatientDAO patientDAO, DentistDAO dentistDAO,
                               TransactionManager transactionManager) {
        this.userDAO = userDAO;
        this.patientDAO = patientDAO;
        this.dentistDAO = dentistDAO;
        this.transactionManager = transactionManager;
    }

    // ------------------------------------------------------------------ patients

    /**
     * Registers a member of the public and the patient record their account is bound to.
     *
     * <p>The account is created active, with role {@link Role#PATIENT}, and can sign in
     * immediately.</p>
     *
     * @param username      the sign-in name they chose
     * @param fullName      their name, which also becomes the patient record's name
     * @param address       their address, required by the patient record
     * @param contactNumber their telephone number
     * @param email         their email address, optional
     * @param password      the password they chose; wiped before this method returns
     * @return the stored account
     * @throws ValidationException if any field is unacceptable; carries every field error at once
     */
    public User registerPatient(String username, String fullName, String address,
                                String contactNumber, String email, char[] password) {

        String cleanUsername = trim(username);
        String cleanFullName = trim(fullName);
        String cleanAddress = trim(address);
        String cleanContact = trim(contactNumber);
        String cleanEmail = trim(email);

        ValidationResult result = new ValidationResult();
        validateUsername(cleanUsername, result);
        validateFullName(cleanFullName, result);
        validateAddress(cleanAddress, result);
        validateContactNumber(cleanContact, result);
        validateOptionalEmail(cleanEmail, result);
        validatePassword(password, result);

        if (result.hasErrors()) {
            wipe(password);
            throw new ValidationException(result);
        }

        // ROLE IS FIXED HERE, NOT SUPPLIED BY THE CALLER.
        User account = buildAccount(cleanUsername, cleanFullName, Role.PATIENT, true, password);

        Patient patient = new Patient();
        patient.setPatientName(cleanFullName);
        patient.setAddress(cleanAddress);
        patient.setContactNumber(cleanContact);
        patient.setEmail(cleanEmail);

        return transactionManager.execute(connection -> {
            int userId = userDAO.insert(account, connection);
            account.setUserId(userId);

            int patientId = patientDAO.insert(patient, connection);
            patientDAO.linkToUser(connection, patientId, userId);

            return account;
        });
    }

    // ------------------------------------------------------------------ dentists

    /**
     * Registers a dentist and the roster entry their account is bound to.
     *
     * <p>The account is created <strong>inactive</strong> and cannot sign in until an
     * administrator activates it. The roster entry is created inactive for the same reason: an
     * unapproved dentist must not appear in the booking screen's dropdown.</p>
     *
     * @param username       the sign-in name they chose
     * @param fullName       their name, which also becomes the roster entry's name
     * @param specialization their field of practice
     * @param licenseNo      their SLMC registration number, unique across the practice
     * @param contactNumber  their telephone number
     * @param password       the password they chose; wiped before this method returns
     * @return the stored account, inactive and awaiting approval
     * @throws ValidationException if any field is unacceptable; carries every field error at once
     */
    public User registerDentist(String username, String fullName, String specialization,
                                String licenseNo, String contactNumber, char[] password) {

        String cleanUsername = trim(username);
        String cleanFullName = trim(fullName);
        String cleanSpecialization = trim(specialization);
        String cleanLicense = trim(licenseNo);
        String cleanContact = trim(contactNumber);

        ValidationResult result = new ValidationResult();
        validateUsername(cleanUsername, result);
        validateFullName(cleanFullName, result);
        validateSpecialization(cleanSpecialization, result);
        validateLicenseNo(cleanLicense, result);
        validateContactNumber(cleanContact, result);
        validatePassword(password, result);

        if (result.hasErrors()) {
            wipe(password);
            throw new ValidationException(result);
        }

        // ROLE IS FIXED HERE, NOT SUPPLIED BY THE CALLER. Inactive until approved.
        User account = buildAccount(cleanUsername, cleanFullName, Role.DENTIST, false, password);

        Dentist dentist = new Dentist();
        dentist.setDentistName(cleanFullName);
        dentist.setSpecialization(cleanSpecialization);
        dentist.setLicenseNo(cleanLicense);
        dentist.setContactNumber(cleanContact);
        // Not bookable until an administrator approves the account.
        dentist.setActive(false);

        return transactionManager.execute(connection -> {
            int userId = userDAO.insert(account, connection);
            account.setUserId(userId);

            int dentistId = dentistDAO.insert(dentist, connection);
            dentistDAO.linkToUser(connection, dentistId, userId);

            return account;
        });
    }

    // ------------------------------------------------------------------ account assembly

    /**
     * Derives the password hash and assembles the account.
     *
     * @param role   supplied only by this class's own call sites, never by a request parameter
     * @param active whether the account may sign in straight away
     */
    private static User buildAccount(String username, String fullName, Role role, boolean active,
                                     char[] password) {

        PasswordHasher.HashedPassword hashed = PasswordHasher.hash(password);

        User user = new User();
        user.setUsername(username);
        user.setFullName(fullName);
        user.setRole(role);
        user.setActive(active);
        user.setPasswordHash(hashed.hash());
        user.setPasswordSalt(hashed.salt());
        user.setHashIterations(hashed.iterations());
        return user;
    }

    // ------------------------------------------------------------------ validation

    private void validateUsername(String username, ValidationResult result) {
        if (username == null) {
            result.addError("username", "Please choose a username.");
            return;
        }
        if (!USERNAME.matcher(username).matches()) {
            result.addError("username",
                    "A username must start with a letter and be 3 to 50 characters of letters, "
                    + "digits, dots or underscores.");
            return;
        }
        if (userDAO.existsByUsername(username)) {
            result.addError("username",
                    "That username is already taken. Please choose another.");
        }
    }

    private static void validateFullName(String fullName, ValidationResult result) {
        if (fullName == null || fullName.length() < 2) {
            result.addError("fullName", "Please enter your full name.");
        } else if (fullName.length() > 100) {
            result.addError("fullName", "The full name may be at most 100 characters.");
        }
    }

    private static void validateAddress(String address, ValidationResult result) {
        if (address == null || address.length() < 5) {
            result.addError("address", "Please enter your address.");
        } else if (address.length() > 255) {
            result.addError("address", "The address may be at most 255 characters.");
        }
    }

    private static void validateContactNumber(String contactNumber, ValidationResult result) {
        if (contactNumber == null) {
            result.addError("contactNumber", "Please enter a contact number.");
        } else if (!CONTACT_NUMBER.matcher(contactNumber).matches()) {
            result.addError("contactNumber",
                    "Please enter a valid contact number of at least 9 digits.");
        }
    }

    /** An email address is optional for a patient, but must be plausible when given. */
    private static void validateOptionalEmail(String email, ValidationResult result) {
        if (email == null) {
            return;
        }
        if (email.length() > 120 || !EMAIL.matcher(email).matches()) {
            result.addError("email", "Please enter a valid email address, or leave it blank.");
        }
    }

    private static void validateSpecialization(String specialization, ValidationResult result) {
        if (specialization == null || specialization.length() < 2) {
            result.addError("specialization", "Please enter your specialisation.");
        } else if (specialization.length() > 100) {
            result.addError("specialization",
                    "The specialisation may be at most 100 characters.");
        }
    }

    private void validateLicenseNo(String licenseNo, ValidationResult result) {
        if (licenseNo == null || licenseNo.length() < 3) {
            result.addError("licenseNo", "Please enter your registration number.");
            return;
        }
        if (licenseNo.length() > 30) {
            result.addError("licenseNo",
                    "The registration number may be at most 30 characters.");
            return;
        }
        // uk_dentists_license would refuse this anyway; checking here turns a database
        // constraint violation into a message beside the field that caused it.
        if (dentistDAO.findByLicenseNo(licenseNo).isPresent()) {
            result.addError("licenseNo",
                    "That registration number is already recorded for another dentist.");
        }
    }

    /**
     * Checks the password without ever copying it into a String, which would leave an immutable
     * plain-text credential on the heap until garbage collection got round to it.
     */
    private static void validatePassword(char[] password, ValidationResult result) {
        if (password == null || password.length == 0) {
            result.addError("password", "Please choose a password.");
            return;
        }
        if (password.length < MIN_PASSWORD_LENGTH) {
            result.addError("password",
                    "The password must be at least " + MIN_PASSWORD_LENGTH + " characters long.");
            return;
        }
        boolean hasLetter = false;
        boolean hasDigit = false;
        boolean hasOther = false;
        for (char c : password) {
            if (Character.isLetter(c)) {
                hasLetter = true;
            } else if (Character.isDigit(c)) {
                hasDigit = true;
            } else {
                hasOther = true;
            }
        }
        if (!hasLetter || !hasDigit || !hasOther) {
            result.addError("password",
                    "The password must contain letters, digits and at least one other character.");
        }
    }

    private static String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static void wipe(char[] password) {
        if (password != null) {
            Arrays.fill(password, '\0');
        }
    }
}
