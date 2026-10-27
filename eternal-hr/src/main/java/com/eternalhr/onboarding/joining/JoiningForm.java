package com.eternalhr.onboarding.joining;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * What the new employee fills in. Field names are the process variable names. Identity and bank numbers are kept
 * to the last 4 digits: the full numbers are on the documents HR collects, not in the workflow.
 */
public class JoiningForm {

  @NotBlank @Size(min = 2, max = 100)
  private String candidateName;
  @NotNull @Past @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
  private LocalDate dateOfBirth;
  @NotBlank @Pattern(regexp = "female|male|other|undisclosed", message = "choose an option")
  private String gender;
  @NotBlank @Size(max = 100)
  private String fatherOrSpouseName;
  @NotBlank @Pattern(regexp = "[6-9][0-9]{9}", message = "enter a 10-digit mobile number")
  private String candidateMobile;
  @NotBlank @Email
  private String candidateEmail;
  @NotBlank @Size(max = 500)
  private String currentAddress;
  @NotBlank @Size(max = 500)
  private String permanentAddress;
  @NotBlank @Size(max = 100)
  private String emergencyContactName;
  @NotBlank @Pattern(regexp = "[0-9+ -]{8,15}", message = "enter a valid phone number")
  private String emergencyContactPhone;
  @NotBlank @Pattern(regexp = "[A-Z]{5}[0-9]{4}[A-Z]", message = "enter a PAN like ABCDE1234F")
  private String panNumber;
  @NotBlank @Pattern(regexp = "[0-9]{4}", message = "enter the last 4 digits")
  private String aadhaarLast4;
  @NotBlank @Size(max = 100)
  private String bankName;
  @NotBlank @Pattern(regexp = "[A-Z]{4}0[A-Z0-9]{6}", message = "enter an IFSC like HDFC0001234")
  private String bankIfsc;
  @NotBlank @Pattern(regexp = "[0-9]{4}", message = "enter the last 4 digits")
  private String bankAccountLast4;
  @NotBlank @Size(max = 100)
  private String highestQualification;
  @Size(max = 100)
  private String previousEmployer;
  @NotNull @DecimalMin("0") @DecimalMax("60")
  private Double totalExperienceYears;
  @AssertTrue(message = "please confirm")
  private boolean declaration;

  // setters normalize what people type differently (case of codes, spaces); binding runs before validation

  private static String trim(String s) {
    return s == null ? null : s.trim();
  }

  private static String upper(String s) {
    return s == null ? null : s.trim().toUpperCase(java.util.Locale.ROOT);
  }

  public String getCandidateName() {
    return candidateName;
  }

  public void setCandidateName(String candidateName) {
    this.candidateName = trim(candidateName);
  }

  public LocalDate getDateOfBirth() {
    return dateOfBirth;
  }

  public void setDateOfBirth(LocalDate dateOfBirth) {
    this.dateOfBirth = dateOfBirth;
  }

  public String getGender() {
    return gender;
  }

  public void setGender(String gender) {
    this.gender = gender;
  }

  public String getFatherOrSpouseName() {
    return fatherOrSpouseName;
  }

  public void setFatherOrSpouseName(String fatherOrSpouseName) {
    this.fatherOrSpouseName = fatherOrSpouseName;
  }

  public String getCandidateMobile() {
    return candidateMobile;
  }

  public void setCandidateMobile(String candidateMobile) {
    this.candidateMobile = trim(candidateMobile);
  }

  public String getCandidateEmail() {
    return candidateEmail;
  }

  public void setCandidateEmail(String candidateEmail) {
    this.candidateEmail = trim(candidateEmail);
  }

  public String getCurrentAddress() {
    return currentAddress;
  }

  public void setCurrentAddress(String currentAddress) {
    this.currentAddress = currentAddress;
  }

  public String getPermanentAddress() {
    return permanentAddress;
  }

  public void setPermanentAddress(String permanentAddress) {
    this.permanentAddress = permanentAddress;
  }

  public String getEmergencyContactName() {
    return emergencyContactName;
  }

  public void setEmergencyContactName(String emergencyContactName) {
    this.emergencyContactName = emergencyContactName;
  }

  public String getEmergencyContactPhone() {
    return emergencyContactPhone;
  }

  public void setEmergencyContactPhone(String emergencyContactPhone) {
    this.emergencyContactPhone = emergencyContactPhone;
  }

  public String getPanNumber() {
    return panNumber;
  }

  public void setPanNumber(String panNumber) {
    this.panNumber = upper(panNumber);
  }

  public String getAadhaarLast4() {
    return aadhaarLast4;
  }

  public void setAadhaarLast4(String aadhaarLast4) {
    this.aadhaarLast4 = trim(aadhaarLast4);
  }

  public String getBankName() {
    return bankName;
  }

  public void setBankName(String bankName) {
    this.bankName = bankName;
  }

  public String getBankIfsc() {
    return bankIfsc;
  }

  public void setBankIfsc(String bankIfsc) {
    this.bankIfsc = upper(bankIfsc);
  }

  public String getBankAccountLast4() {
    return bankAccountLast4;
  }

  public void setBankAccountLast4(String bankAccountLast4) {
    this.bankAccountLast4 = trim(bankAccountLast4);
  }

  public String getHighestQualification() {
    return highestQualification;
  }

  public void setHighestQualification(String highestQualification) {
    this.highestQualification = highestQualification;
  }

  public String getPreviousEmployer() {
    return previousEmployer;
  }

  public void setPreviousEmployer(String previousEmployer) {
    this.previousEmployer = previousEmployer;
  }

  public Double getTotalExperienceYears() {
    return totalExperienceYears;
  }

  public void setTotalExperienceYears(Double totalExperienceYears) {
    this.totalExperienceYears = totalExperienceYears;
  }

  public boolean isDeclaration() {
    return declaration;
  }

  public void setDeclaration(boolean declaration) {
    this.declaration = declaration;
  }
}
