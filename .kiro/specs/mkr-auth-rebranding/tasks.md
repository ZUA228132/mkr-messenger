# Implementation Plan

- [x] 1. Update registration flow to require PIN setup
  - [x] 1.1 Modify MKRAuthScreen to navigate to PIN setup after successful registration
    - Update onAuthSuccess callback to check if PIN is already set
    - If not set, navigate to SetupPinScreen with isRequired=true
    - _Requirements: 1.6, 2.1_
  - [x] 1.2 Update SetupPinScreen to support required mode
    - Add isRequired parameter to hide skip button
    - Update navigation to go to ChatList after PIN set
    - _Requirements: 2.2, 2.3, 2.6_
  - [x] 1.3 Update PioneerNavHost navigation logic
    - Add new navigation path: Auth → SetupPin → ChatList for new users
    - Keep existing path: Auth → ChatList for returning users with PIN
    - _Requirements: 2.1, 2.6_
  - [x] 1.4 Write property test for PIN validation
    - **Property 4: PIN Format Validation**
    - **Validates: Requirements 2.2**
  - [x] 1.5 Write property test for PIN confirmation
    - **Property 5: PIN Confirmation Matching**
    - **Validates: Requirements 2.4, 2.5**

- [x] 2. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 3. Rename LIMO to MKR throughout the application
  - [x] 3.1 Update UI strings and labels
    - Replace "LIMO" with "MKR" in all user-visible text
    - Update LIMO_MESSENGER.md and LIMO_FEATURES.md file names and content
    - _Requirements: 4.1, 4.3_
  - [x] 3.2 Update official channel references
    - Change "limo-official-channel" to "mkr-official-channel" in ChatListViewModel
    - Update channel name display to "MKR Official"
    - _Requirements: 4.2, 5.2, 5.3_
  - [x] 3.3 Update component and theme naming
    - Rename LimoNavItem to MKRNavItem in ChatListScreen
    - Rename LimoTab, LimoMainScreen, LimoBottomNavigation in MKRMainScreen.kt
    - Update StealthMode.kt disguise type names
    - _Requirements: 4.4_
  - [x] 3.4 Update network and API references
    - Update SERVER_HOST in NetworkSecurity.kt if needed
    - Update any API endpoints referencing "limo"
    - _Requirements: 4.1_

- [x] 4. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 5. Add validation for registration form
  - [x] 5.1 Implement callsign validation function
    - Validate lowercase letters, digits, underscores only
    - Validate minimum 3 characters
    - _Requirements: 1.2, 1.3_
  - [x] 5.2 Implement password validation
    - Validate minimum 6 characters
    - Validate password confirmation match
    - _Requirements: 1.4, 1.5_
  - [x] 5.3 Write property test for callsign validation
    - **Property 1: Callsign Character Validation**
    - **Validates: Requirements 1.2**
  - [ ]* 5.4 Write property test for input length validation
    - **Property 2: Input Length Validation**
    - **Validates: Requirements 1.3, 1.4**
  - [ ]* 5.5 Write property test for password matching
    - **Property 3: Password Matching**
    - **Validates: Requirements 1.5**

- [x] 6. Final Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.
