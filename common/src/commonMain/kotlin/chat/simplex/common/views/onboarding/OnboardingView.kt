package chat.simplex.common.views.onboarding

enum class OnboardingStage {
  Step0_1_FeatureNoIdentity,
  Step0_2_FeatureEncryption,
  Step0_3_FeatureDecentralized,
  Step0_4_FeatureCryptoIdentity,
  Step1_SimpleXInfo,
  Step2_CreateProfile,
  LinkAMobile,
  Step2_4_ChooseUnlockMethod,
  Step2_4_5_SetupYubiKey,
  Step2_4_5_0_YubiKeyFactoryReset,
  Step2_4_5_1_SetupYubiKeyPIN,
  Step2_4_5_2_SetupYubiKeyPUK,
  Step2_4_5_3_SetupYubiKeyManagementKey,
  Step2_4_5_4_EncryptDatabaseYubiKey,
  Step2_5_SetupDatabasePassphrase,
  Step2_6_SetupSelfDestruct,
  Step3_ChooseServerOperators,
  Step3_CreateSimpleXAddress,
  Step4_SetNotificationsMode,
  OnboardingComplete
}
