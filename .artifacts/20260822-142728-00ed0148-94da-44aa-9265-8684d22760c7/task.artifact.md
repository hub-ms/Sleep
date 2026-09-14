# Task Management

- [ ] Refine Profile and Account Settings UX
    - [ ] **Backend (Spring Boot)**
        - [ ] Add profile update API (`PATCH /auth/me`)
        - [ ] Support withdrawal reason in delete API
    - [ ] **Shared Module (Common)**
        - [ ] Update `AuthApi` and `AuthRepository` for profile updates
        - [ ] Implement `SaveProfile` and `WithdrawalReason` intents in `AuthViewModel`
    - [ ] **UI Components (Compose)**
        - [ ] Fix `ProfileEditContent`:
            - [ ] Connect form to ViewModel `SaveProfile` intent
            - [ ] Implement camera menu (Default/Album/Camera)
        - [ ] Standardize Snackbar design for clipboard copy
        - [ ] Style `AccountConnectItem` buttons/badges to match system
        - [ ] Implement 3-step withdrawal flow screens
    - [ ] **Navigation**
        - [ ] Update `AppScreens.kt` for the new flow
    - [ ] **Verification**
        - [ ] Build and test all flows
