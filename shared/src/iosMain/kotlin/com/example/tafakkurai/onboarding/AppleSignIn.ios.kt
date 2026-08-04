package com.example.tafakkurai.onboarding

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AuthenticationServices.ASAuthorization
import platform.AuthenticationServices.ASAuthorizationAppleIDCredential
import platform.AuthenticationServices.ASAuthorizationAppleIDProvider
import platform.AuthenticationServices.ASAuthorizationController
import platform.AuthenticationServices.ASAuthorizationControllerDelegateProtocol
import platform.AuthenticationServices.ASAuthorizationControllerPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASAuthorizationScopeEmail
import platform.AuthenticationServices.ASAuthorizationScopeFullName
import platform.AuthenticationServices.ASPresentationAnchor
import platform.Foundation.NSError
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.UIKit.UIApplication
import platform.darwin.NSObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** `ASAuthorizationError.canceled` — the user swiped the sheet away or hit Cancel. */
private const val ASAuthorizationErrorCanceled = 1001L

/**
 * Bridges the two Apple delegate callbacks onto one coroutine. Also serves as its own
 * presentation-context provider, so the sheet has a window to anchor to.
 *
 * ASAuthorizationController holds its delegate **weakly**, so this object must be kept alive by
 * something else for the whole flow — see [aliveDelegate]. Without that it is collected between
 * `performRequests()` and the callback, and the sheet closes with nothing ever resuming.
 */
@OptIn(ExperimentalForeignApi::class)
private class AppleSignInDelegate(
    val onSuccess: (AppleCredential) -> Unit,
    val onFailure: (Throwable) -> Unit,
) : NSObject(),
    ASAuthorizationControllerDelegateProtocol,
    ASAuthorizationControllerPresentationContextProvidingProtocol {

    override fun authorizationController(
        controller: ASAuthorizationController,
        didCompleteWithAuthorization: ASAuthorization,
    ) {
        val cred = didCompleteWithAuthorization.credential as? ASAuthorizationAppleIDCredential
        if (cred == null) {
            onFailure(IllegalStateException("Apple returned a credential of an unexpected type"))
            return
        }

        // identityToken is the signed JWT the backend verifies; everything else is a nicety.
        val token = cred.identityToken?.let {
            NSString.create(data = it, encoding = NSUTF8StringEncoding)?.toString()
        }
        if (token.isNullOrBlank()) {
            onFailure(IllegalStateException("Apple returned no identity token"))
            return
        }

        // Only ever non-null on the first authorization for this Apple ID + app pair.
        val name = cred.fullName?.let { n ->
            listOfNotNull(n.givenName, n.familyName).joinToString(" ").ifBlank { null }
        }

        onSuccess(AppleCredential(idToken = token, fullName = name, email = cred.email))
    }

    override fun authorizationController(
        controller: ASAuthorizationController,
        didCompleteWithError: NSError,
    ) {
        onFailure(
            if (didCompleteWithError.code == ASAuthorizationErrorCanceled) AppleSignInCancelled()
            else IllegalStateException(didCompleteWithError.localizedDescription),
        )
    }

    override fun presentationAnchorForAuthorizationController(
        controller: ASAuthorizationController,
    ): ASPresentationAnchor = UIApplication.sharedApplication.keyWindow
}

/** Keeps the in-flight delegate from being collected while the sheet is up (see the class doc). */
private var aliveDelegate: AppleSignInDelegate? = null

@OptIn(ExperimentalForeignApi::class)
actual suspend fun appleSignIn(): AppleCredential = suspendCancellableCoroutine { cont ->
    // Apple calls back exactly once, but resuming twice would crash the app, so guard it.
    var settled = false
    fun settle(block: () -> Unit) {
        if (settled) return
        settled = true
        aliveDelegate = null
        block()
    }

    val delegate = AppleSignInDelegate(
        onSuccess = { credential -> settle { cont.resume(credential) } },
        onFailure = { error -> settle { cont.resumeWithException(error) } },
    )
    aliveDelegate = delegate

    val request = ASAuthorizationAppleIDProvider().createRequest().apply {
        // Asking for both is what makes Apple show the name/"Hide My Email" rows on the sheet.
        requestedScopes = listOf(ASAuthorizationScopeFullName, ASAuthorizationScopeEmail)
    }

    val controller = ASAuthorizationController(authorizationRequests = listOf(request))
    controller.delegate = delegate
    controller.presentationContextProvider = delegate
    controller.performRequests()

    // If the caller's scope dies (screen left) drop our reference; the sheet dismisses itself.
    cont.invokeOnCancellation { aliveDelegate = null }
}
