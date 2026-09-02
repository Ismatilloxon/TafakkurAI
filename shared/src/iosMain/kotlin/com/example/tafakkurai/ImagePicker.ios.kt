package com.example.tafakkurai

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.UIKit.*
import platform.darwin.NSObject
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    val out = ByteArray(size)
    if (size > 0) out.usePinned { memcpy(it.addressOf(0), bytes, length) }
    return out
}

private class PickerDelegate(
    val onResult: (ByteArray?) -> Unit,
) : NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {

    override fun imagePickerController(
        picker: UIImagePickerController,
        didFinishPickingMediaWithInfo: Map<Any?, *>,
    ) {
        val image = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage
        val bytes = image
            ?.let { UIImageJPEGRepresentation(it, 0.82) }
            ?.toByteArray()
        picker.dismissViewControllerAnimated(true, completion = { onResult(bytes) })
    }

    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        picker.dismissViewControllerAnimated(true, completion = { onResult(null) })
    }
}

private fun UIViewController.presentationHost(): UIViewController {
    var host = this
    while (host.presentedViewController != null) {
        host = host.presentedViewController ?: break
    }
    return host
}

@Composable
actual fun rememberImagePicker(onResult: (ByteArray?) -> Unit): () -> Unit {
    // delegate must outlive the presentation
    val delegate = remember { PickerDelegate(onResult) }
    return {
        val picker = UIImagePickerController()
        picker.sourceType =
            UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypePhotoLibrary
        picker.delegate = delegate
        val root = UIApplication.sharedApplication.keyWindow?.rootViewController
        root?.presentationHost()?.presentViewController(picker, animated = true, completion = null)
    }
}
