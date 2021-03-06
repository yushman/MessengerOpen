package ooo.emessi.messenger.ui.activities

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.FileProvider
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.r0adkll.slidr.Slidr
import ooo.emessi.messenger.BuildConfig
import ooo.emessi.messenger.R
import ooo.emessi.messenger.constants.Constants
import ooo.emessi.messenger.constants.Constants.FORWARDED_MESSAGE_ID
import ooo.emessi.messenger.constants.Constants.KEY_CHAT
import ooo.emessi.messenger.data.model.dto_model.chat.ChatDto
import ooo.emessi.messenger.data.model.dto_model.message.MessageDto
import ooo.emessi.messenger.data.model.view_item_model.chat.ChatViewItem
import ooo.emessi.messenger.data.model.view_item_model.message.MessageListViewItem
import ooo.emessi.messenger.data.model.view_item_model.message.MessageViewItemContent
import ooo.emessi.messenger.ui.adapters.MessageItemTouchHelperCallback
import ooo.emessi.messenger.ui.adapters.MessagesAdapter
import ooo.emessi.messenger.ui.fragments.BottomAttachDialogFragment
import ooo.emessi.messenger.ui.fragments.BottomMessageActionFragment
import ooo.emessi.messenger.ui.viewmodels.ChatViewModelFactory
import ooo.emessi.messenger.ui.viewmodels.MucLightChatActivityViewModel
import ooo.emessi.messenger.utils.getCameraFilePath
import ooo.emessi.messenger.utils.getPath
import ooo.emessi.messenger.utils.helpers.AvatarHelper
import ooo.emessi.messenger.utils.helpers.KeyboardHelper
import ooo.emessi.messenger.utils.helpers.SoundHelper

class MucLightChatActivity : AppCompatActivity() {

    private val TAG = this.javaClass.simpleName

    val FILE_PICKER_REQUEST_CODE = 17*13
    val IMAGE_PICKER_REQUEST_CODE = 17*17
    val CAMERA_REQUEST_CODE = 17*21

    private lateinit var messagesAdapter: MessagesAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var etInput: EditText
    private lateinit var btnSend: ImageView
    private lateinit var btnAttach: ImageView
    private lateinit var btnCamera: ImageView
    private lateinit var chatViewModel: MucLightChatActivityViewModel
    private lateinit var toolbar: Toolbar
    private lateinit var tvChatName: TextView
    private lateinit var ivAvatar: ImageView
    private lateinit var layoutMessageAction: ConstraintLayout
    private lateinit var ivCloseMessageAction: ImageView
    private lateinit var tvMessageActionText: TextView
    private lateinit var tvMessageActionDescription: TextView

    private lateinit var chatDto: ChatDto
    var currentCameraPicturePath = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_muc_light_chat)

        initFromBundle()
        initViewModel()
        initViews()

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK) {
            when(requestCode){
                FILE_PICKER_REQUEST_CODE, IMAGE_PICKER_REQUEST_CODE -> {
                    if (data != null) {
                        val paths = arrayListOf<String>()
                        val clipData = data.clipData
                        val uris = arrayListOf<Uri>()
                        if (clipData != null) {
                            for (i in 0 until clipData.itemCount){
                                uris.add(clipData.getItemAt(i).uri)
                            }
                        } else uris.add(data.data!!)
                        uris.forEach {
                            paths.add(getPath(this, it)!!)
                        }
                        if (paths.isNotEmpty()) {
                            Log.d(TAG, paths.toString())
                            addAttachmentsToModel(paths)
                        }
                        else Log.d(TAG, "path empty")
                    }
                }
                CAMERA_REQUEST_CODE -> {
                    if (currentCameraPicturePath.isNotEmpty()) {
                        Log.d(TAG, currentCameraPicturePath)
                        addAttachmentsToModel(listOf(currentCameraPicturePath))
                    } else Log.d(TAG, "path empty")
                }
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onResume() {
        chatViewModel.flushUnread()
        chatViewModel.loadChatInfo()
        super.onResume()
    }

    override fun onPause() {
        chatViewModel.flushUnread()
        super.onPause()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.muc_chat_menu, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        Log.d("Menu", "clicked")
        when (item!!.itemId) {
            R.id.menu_muc_chat_chat_info -> chatInfoClick()
            R.id.menu_muc_chat_clear_history -> chatClearHistoryClick()
            R.id.menu_muc_chat_delete_chat -> chatDeleteChat()
        }
        return super.onOptionsItemSelected(item)
    }

    private fun chatDeleteChat() {
        chatViewModel.deleteChat()
        finish()
    }

    private fun chatClearHistoryClick() {
        chatViewModel.clearChatHistory()
    }

    private fun chatInfoClick() {
        routeToChatInfoActivity()
    }

    private fun routeToChatInfoActivity() {
        val i = Intent(this, MuclightChatInfoActivity::class.java)
        i.putExtra(KEY_CHAT, chatDto)
        startActivity(i)
    }

    private fun initFromBundle() {

        try {
            val bundle: Bundle? = intent.extras

            if (bundle != null) {
                chatDto = bundle.getParcelable<ChatDto>(KEY_CHAT)!!
            }
        } catch (ex: Exception) {
            Log.d(TAG, ex.toString())
        }

    }

    private fun initViewModel() {
        chatViewModel = ViewModelProvider(this, ChatViewModelFactory(chatDto)).get(MucLightChatActivityViewModel::class.java)
        chatViewModel.messageItems.observe(this, Observer { messagesAdapter.updateMessages(it)
            if (messagesAdapter.itemCount != 0) {recyclerView.scrollToPosition(messagesAdapter.itemCount - 1)}
        })
        chatViewModel.chatViewItem.observe(this, Observer { updateUI(it)})
        chatViewModel.messageSended.observe(this, Observer { playSendSound(it) })
    }

    private fun initViews() {
        title = ""
        toolbar = findViewById(R.id.toolbar_muc_chat)
        setSupportActionBar(toolbar)
        toolbar.title = ""

        toolbar.setNavigationOnClickListener { finish() }

        recyclerView = findViewById(R.id.rv_messages)
        etInput = findViewById(R.id.et_input)
        btnSend = findViewById(R.id.btn_send)
        btnAttach = findViewById(R.id.btn_attach)
        btnCamera = findViewById(R.id.btn_camera)
        tvChatName = findViewById(R.id.tv_name_muc_chat)
        ivAvatar = findViewById(R.id.iv_avatar_muc_chat)
        layoutMessageAction = findViewById(R.id.layout_message_reply)
        ivCloseMessageAction = findViewById(R.id.iv_close_action)
        tvMessageActionText = findViewById(R.id.tv_message_action_text)
        tvMessageActionDescription = findViewById(R.id.tv_message_action_description)

        layoutMessageAction.visibility = View.GONE
        etInput.isFocusableInTouchMode = true
        etInput.showSoftInputOnFocus = true

        tvChatName.text = chatDto.name
//        AvatarHelper.placeRoundRectAvatar(ivAvatar, null, chatId.capitalize().removeRange(2, chatId.length), chatId)

        messagesAdapter = MessagesAdapter{ v, message -> showMessageActionDialog(v, message)}

        val lm = LinearLayoutManager(this)
        //lm.reverseLayout = true
        lm.stackFromEnd = true

        recyclerView.layoutManager = lm
        recyclerView.adapter = messagesAdapter
        recyclerView.itemAnimator = null

        val touchCallback = MessageItemTouchHelperCallback(messagesAdapter) { replySwipe(it) }
        val touchHelper = ItemTouchHelper(touchCallback)
        touchHelper.attachToRecyclerView(recyclerView)

        btnSend.setOnClickListener { sendMessage() }
        btnAttach.setOnClickListener { showBottomAttachDialog() }
        btnCamera.setOnClickListener { showCamera() }
        tvChatName.setOnClickListener { chatInfoClick() }
        ivAvatar.setOnClickListener { chatInfoClick() }
        ivCloseMessageAction.setOnClickListener { flushMessageActions() }
        setBtnSendEnabled(false)
        etInput.addTextChangedListener { setBtnSendEnabled(etInput.text.isNotEmpty()) }

        Slidr.attach(this)

    }

    private fun updateUI(chatViewItem: ChatViewItem) {
        tvChatName.text = chatViewItem.chatDto.name
        chatViewItem.contactDto?.let {
            AvatarHelper.placeRoundAvatar(ivAvatar, it.avatar, it.getShortName(), it.contactJid)
        }
    }

    private fun setBtnSendEnabled(b: Boolean) {
        if (b) {
            btnSend.visibility = View.VISIBLE
            btnCamera.visibility = View.GONE
            btnAttach.visibility = View.GONE
        } else {
            btnSend.visibility = View.GONE
            btnCamera.visibility = View.VISIBLE
            btnAttach.visibility = View.VISIBLE
        }
        btnSend.isEnabled = b
    }

    private fun sendMessage() {

        val messageBody = etInput.text.toString()

        chatViewModel.sendMessage(messageBody)
        flushMessageActions()
    }

    private fun showMessageActionDialog(v: View, message: MessageViewItemContent.MessageItem) {
        KeyboardHelper.hideKeyboard(etInput, this)
        val bsf = BottomMessageActionFragment(message.message.messageDto, message.isEditable) {view -> proceedMessageAction(view, message.message.messageDto)}
        bsf.show(supportFragmentManager, bsf.tag)
    }

    private fun proceedMessageAction(view: View, it: MessageDto) {
        when (view.id){
            R.id.tv_reply_message_action -> replyClick(it)
            R.id.tv_forward_message_action -> forwardClick(it)
            R.id.tv_edit_message_action -> editClick(it)
            R.id.tv_copy_message_action -> copyClick(it)
            R.id.tv_delete_message_action -> deleteClick(it)
        }
    }

    private fun editClick(it: MessageDto) {
        if (it.payloadType == MessageDto.PayloadType.NONE) {
            layoutMessageAction.visibility = View.VISIBLE
            tvMessageActionDescription.text = "Edit"
            tvMessageActionText.text = it.body
            etInput.isFocusable = true
            etInput.isFocusableInTouchMode = true
            etInput.showSoftInputOnFocus = true
            etInput.requestFocus()
            etInput.setText(it.body)
            etInput.setSelection(it.body.length)
            chatViewModel.setCorrectedMessage(it)
            KeyboardHelper.showKeyboard(etInput, this)
        }
    }

    private fun copyClick(it: MessageDto) {
        if (it.payloadType == MessageDto.PayloadType.NONE) {
            val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clipData = ClipData.newPlainText(Constants.CLIP_LABEL, it.body)
            clipboardManager.setPrimaryClip(clipData)
        }
    }

    private fun deleteClick(it: MessageDto) {
        chatViewModel.deleteMessage(it)
    }

    private fun forwardClick(it: MessageDto) {
        val i = Intent(this, NewMainActivity::class.java)
        i.putExtra(FORWARDED_MESSAGE_ID, it.id)
        startActivity(i)
        finish()
    }

    private fun replyClick(it: MessageDto) {
        layoutMessageAction.visibility = View.VISIBLE
        tvMessageActionDescription.text = it.from
        tvMessageActionText.text = it.body
        etInput.isFocusable = true
        etInput.setText("")
        etInput.setSelection(0)
        chatViewModel.setReplyedMessage(it)
        KeyboardHelper.showKeyboard(etInput, this)
    }

    private fun replySwipe(it: MessageListViewItem) {
        val messageContent = it.content as MessageViewItemContent.MessageItem
        SoundHelper.vibrate(this)
        replyClick(messageContent.message.messageDto)
    }

    private fun showBottomAttachDialog() {
        val bsf = BottomAttachDialogFragment{view, list ->  attachItemClick(view, list)}
        bsf.show(supportFragmentManager, bsf.tag)
    }

    private fun attachItemClick(view: View, list: List<String>) {
        when (view.id) {
            R.id.iv_bottom_attach_camera -> showCamera()
            R.id.iv_bottom_attach_gallery -> showGallery()
            R.id.iv_bottom_attach_storage -> showStorage()
            R.id.iv_bottom_attach_send -> sendSelectedImages(list)
        }
    }

    private fun showCamera() {
        val file = getCameraFilePath()//.absolutePath
        val uri = FileProvider.getUriForFile(this, BuildConfig.APPLICATION_ID + ".provider", file)
        val i = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        i.putExtra(MediaStore.EXTRA_OUTPUT, uri)
        i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        currentCameraPicturePath =  file.absolutePath
        startActivityForResult(i, CAMERA_REQUEST_CODE)
    }

    private fun showStorage() {
        val i = Intent(Intent.ACTION_GET_CONTENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE)
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
        startActivityForResult(i, FILE_PICKER_REQUEST_CODE)
    }

    private fun showGallery() {
        val i = Intent(Intent.ACTION_GET_CONTENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE)
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        startActivityForResult(i, IMAGE_PICKER_REQUEST_CODE)
    }

    private fun sendSelectedImages(list: List<String>) {
        chatViewModel.sendAttachments(list)
    }

    private fun addAttachmentsToModel(docPaths: List<String>) {
        chatViewModel.sendAttachments(docPaths)
    }

    private fun playSendSound(message: MessageDto) {
        if (!message.isSended) return
        SoundHelper.playSendSound(this)
        SoundHelper.vibrate(this)
    }

    private fun flushMessageActions() {
        etInput.text.clear()
        chatViewModel.flushMessageActions()
        layoutMessageAction.visibility = View.GONE
    }
}
