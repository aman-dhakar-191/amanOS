package com.amanOS.core

object AgentContract {

    object Contacts {
        const val AUTHORITY = "com.amanOS.contacts.provider"
        const val URI_CONTACTS = "content://$AUTHORITY/contacts"
        const val URI_CALL_HISTORY = "content://$AUTHORITY/call_history"

        const val ACTION_CALL = "com.amanOS.contacts.action.CALL"
        const val ACTION_ADD = "com.amanOS.contacts.action.ADD"
        const val ACTION_EDIT = "com.amanOS.contacts.action.EDIT"
        const val ACTION_DELETE = "com.amanOS.contacts.action.DELETE"

        const val EVENT_CONTACT_ADDED = "com.amanOS.contacts.event.CONTACT_ADDED"
        const val EVENT_CONTACT_UPDATED = "com.amanOS.contacts.event.CONTACT_UPDATED"
        const val EVENT_CONTACT_DELETED = "com.amanOS.contacts.event.CONTACT_DELETED"
        const val EVENT_CALL_STARTED = "com.amanOS.contacts.event.CALL_STARTED"
        const val EVENT_CALL_ENDED = "com.amanOS.contacts.event.CALL_ENDED"
        const val EVENT_CALL_FAILED = "com.amanOS.contacts.event.CALL_FAILED"

        const val EXTRA_CONTACT_ID = "contact_id"
        const val EXTRA_NAME = "name"
        const val EXTRA_PHONE = "phone"
        const val EXTRA_EMAIL = "email"
        const val EXTRA_NUMBER = "number"
        const val EXTRA_DURATION = "duration"
        const val EXTRA_TIMESTAMP = "timestamp"
    }

    object Messaging {
        const val AUTHORITY = "com.amanOS.messaging.provider"
        const val URI_THREADS = "content://$AUTHORITY/threads"
        const val URI_MESSAGES = "content://$AUTHORITY/messages"

        const val ACTION_SEND = "com.amanOS.messaging.action.SEND"
        const val ACTION_DELETE_THREAD = "com.amanOS.messaging.action.DELETE_THREAD"
        const val ACTION_MARK_READ = "com.amanOS.messaging.action.MARK_READ"
        const val ACTION_OPEN_THREAD = "com.amanOS.messaging.action.OPEN_THREAD"

        const val EVENT_SMS_RECEIVED = "com.amanOS.messaging.event.SMS_RECEIVED"
        const val EVENT_SMS_SENT = "com.amanOS.messaging.event.SMS_SENT"
        const val EVENT_SMS_DELIVERED = "com.amanOS.messaging.event.SMS_DELIVERED"
        const val EVENT_SMS_FAILED = "com.amanOS.messaging.event.SMS_FAILED"
        const val EVENT_THREAD_DELETED = "com.amanOS.messaging.event.THREAD_DELETED"
        const val EVENT_MARKED_READ = "com.amanOS.messaging.event.MARKED_READ"

        const val EXTRA_TO = "to"
        const val EXTRA_BODY = "body"
        const val EXTRA_THREAD_ID = "thread_id"
        const val EXTRA_ADDRESS = "address"
        const val EXTRA_TIMESTAMP = "timestamp"
    }

    // Add more module objects here as modules are built:
    // object Notes { ... }
    // object Tasks { ... }
    // object Device { ... }
    // object Calendar { ... }
    // object Location { ... }

    object Extras {
        const val MODULE = "agent_module"
        const val EVENT = "agent_event"
        const val RESULT_CODE = "result_code"
        const val RESULT_MESSAGE = "result_message"
        const val TIMESTAMP = "timestamp"
        const val REPLY_TO_PACKAGE = "reply_to_package"
        const val REPLY_TO_ACTION = "reply_to_action"
    }

    object ResultCode {
        const val SUCCESS = 200
        const val NOT_FOUND = 404
        const val ERROR = 500
        const val PERMISSION_DENIED = 403
    }
}
