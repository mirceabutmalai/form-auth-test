/*!
 * RADCOM.
 * 
 */

/**
 * function expression for UMD module style of LOGGER module.
 * this code needs the following Polyfill modules:
 * - nothing for the moment
 * @param {!Object} root object
 * @param {function (!Object): !Object} factory function called in order to obtain exported object
 * @return {undefined} no return value
 */
(function (root, factory) {
    "use strict";

    if ((typeof define === 'function') && (define['amd'])) {
        // AMD. Register as an module.
        define([], function () {
            return factory(console);
        });
    } else if ((typeof exports === 'object') && (typeof exports['nodeName'] !== 'string')) {
        // CommonJS
        exports['RdcLogger'] = factory(console);
    } else {
        // Browser globals --> the most traditional, historical and safe method
        root['RdcLogger'] = factory(console);
    }

})(((('object' === typeof window) && (window))
        || (('object' === typeof self) && (self))
        || (('object' === typeof global) && (global))
        || ({})), function (console) {

    "use strict";

    if (typeof console !== 'object') {
        throw new Error('RdcLogger\'s JavaScript requires console global object');
    }

    const cVersion = "1.0.res08";

    const localLevelVerbose = 1;
    const localLevelInfo = 2;
    const localLevelWarning = 3;
    const localLevelError = 4;

    /**
     * @typedef {{
     *     eventDate: !Date,
     *     category: !string,
     *     level: !number,
     *     message: !string,
     *     args: Iterable
     * }}
     */
    var TypeLogEvent;

    /**
     * check if object property has empty value.
     * @param {?Object} pObj object to check property
     * @param {?string} pProperty property name
     * @return {!boolean} true if property is empty
     */
    var gIsEmpty = function (pObj, pProperty) {
        if ('undefined' === typeof pObj) {
            return true;
        } else if (null === pObj) {
            return true;
        } else if ('undefined' === typeof pProperty) {
            return true;
        } else if (null === pProperty) {
            return true;
        } else if ('' === pProperty) {
            return true;
        } else if ('undefined' === typeof pObj[pProperty]) {
            return true;
        } else if (null === pObj[pProperty]) {
            return true;
        }
        return false;
    };

    /**
     * extract property value from object.
     * @param {?Object} pObj object to extract property
     * @param {?string} pProperty property name
     * @param {*} pDefaultValue default value
     * @return {*} extracted value
     */
    var gProp = function (pObj, pProperty, pDefaultValue) {
        if (gIsEmpty(pObj, pProperty)) {
            return pDefaultValue;
        }
        return pObj[pProperty];
    };

    /**
     * constructor for logger object.
     * @param {!Object} pClientOpt object with properties for initialization
     * @final
     * @constructor
     */
    var aLogger = function (pClientOpt) {
        if (gIsEmpty(pClientOpt, 'category')) {
            throw new Error('missing mandatory argument category');
        }

        /**
         * configuration options.
         * @type {!Object}
         * @private
         */
        this.options = pClientOpt;
        /**
         * logging category.
         * @type {!string}
         * @private
         */
        this.category = /** @type {string} */ (gProp(pClientOpt, 'category', null));
        /**
         * logging level.
         * @type {!number}
         * @private
         */
        this.level = /** @type {!number} */ (gProp(pClientOpt, 'level', localLevelWarning));

        var self = this;

        /**
         * api object returned to caller.
         * @type {!Object}
         * @private
         */
        this.api = (function () {
            var rval = {};

            /**
             * log at trace level.
             * @param {!string} msg message to be logged
             * @param {...Object} moreArgs arguments referenced from message parameter
             * @returns {undefined} no return value
             */
            rval['trace'] = function (msg, ...moreArgs) {
                self._log.apply(self, [localLevelVerbose, msg, moreArgs]);
            };
            /**
             * log at debug level (same as trace level).
             * @param {!string} msg message to be logged
             * @param {...Object} moreArgs arguments referenced from message parameter
             * @returns {undefined} no return value
             */
            rval['debug'] = function (msg, ...moreArgs) {
                self._log.apply(self, [localLevelVerbose, msg, moreArgs]);
            };

            /**
             * log at normal log level.
             * @param {!string} msg message to be logged
             * @param {...Object} moreArgs arguments referenced from message parameter
             * @returns {undefined} no return value
             */
            rval['log'] = function (msg, ...moreArgs) {
                self._log.apply(self, [localLevelInfo, msg, moreArgs]);
            };
            /**
             * log at info level (same as normal log level).
             * @param {!string} msg message to be logged
             * @param {...Object} moreArgs arguments referenced from message parameter
             * @returns {undefined} no return value
             */
            rval['info'] = function (msg, ...moreArgs) {
                self._log.apply(self, [localLevelInfo, msg, moreArgs]);
            };

            /**
             * log at warning level.
             * @param {!string} msg message to be logged
             * @param {...Object} moreArgs arguments referenced from message parameter
             * @returns {undefined} no return value
             */
            rval['warn'] = function (msg, ...moreArgs) {
                self._log.apply(self, [localLevelWarning, msg, moreArgs]);
            };

            /**
             * log at error level.
             * @param {!string} msg message to be logged
             * @param {...Object} moreArgs arguments referenced from message parameter
             * @returns {undefined} no return value
             */
            rval['error'] = function (msg, ...moreArgs) {
                self._log.apply(self, [localLevelError, msg, moreArgs]);
            };

            /**
             * check if this logger instance is enabled for trace logging.
             * @returns {!boolean} true if trace is enabled
             */
            rval['isTraceEnabled'] = function () {
                return (self.level >= localLevelVerbose);
            };
            /**
             * check if this logger instance is enabled for specified logging level.
             * @param {!number} pLevel logging level to be check
             * @returns {!boolean} true if log level is enabled
             */
            rval['isEnabledFor'] = function (pLevel) {
                return (self.level >= pLevel);
            };

            return rval;
        })();

        return this.api;
    };

    aLogger.prototype = {};

    // -------------------------------------------------------------------
    // client api entry points
    // -------------------------------------------------------------------

    /**
     * performs logging action.
     * @param {!number} pEventLevel log level for log event
     * @param {!string} pMsg log message
     * @param {!Iterable} pMoreArgs supplementary arguments passed to original call
     * @returns {undefined} no return value
     * @this {aLogger}
     * @private
     */
    aLogger.prototype._log = function (pEventLevel, pMsg, pMoreArgs) {
        if (pEventLevel < this.level) {
            return;
        }

        var aToLogMsg;
        if ((typeof pMsg === 'undefined') || (pMsg === null)) {
            aToLogMsg = '';
        } else {
            aToLogMsg = pMsg.toString();
        }

        /**
         * @type {!TypeLogEvent}
         */
        var aLogEvent = {
            eventDate: new Date(),
            category: this.category,
            level: pEventLevel,
            message: aToLogMsg,
            args: pMoreArgs
        };

        // aici putem stoca local aceste aLogEvent pentru a putea periodic sa le urcam pe server
        // de unde putem sa le scoatem acolo in fisierul de log

        var aConsoleMsg = this._formatLogMessage(aLogEvent);

        if (pEventLevel === localLevelVerbose) {
            console.trace(aConsoleMsg, ...pMoreArgs);
        } else if (pEventLevel === localLevelInfo) {
            console.log(aConsoleMsg, ...pMoreArgs);
        } else if (pEventLevel === localLevelWarning) {
            console.warn(aConsoleMsg, ...pMoreArgs);
        } else if (pEventLevel === localLevelError) {
            console.error(aConsoleMsg, ...pMoreArgs);
        } else {
            console.log(aConsoleMsg, ...pMoreArgs);
        }
    };

    /**
     * formats a log event in order to be logged.
     * @param {!TypeLogEvent} pLogEvent log event
     * @returns {!string} log text
     * @this {aLogger}
     * @private
     */
    aLogger.prototype._formatLogMessage = function (pLogEvent) {
        if ((typeof pLogEvent === 'undefined') || (pLogEvent === null)) {
            return '';
        }

        var aStrRval = '';
        //aStrRval += pLogEvent.eventDate.toISOString() + ' ';
        aStrRval += pLogEvent.eventDate.toLocaleString() + ' ';
        aStrRval += pLogEvent.category + ': ';
        aStrRval += pLogEvent.message;

        return aStrRval;
    };

    // nivelele de logare global vizibile
    aLogger['levelVerbose'] = localLevelVerbose;
    aLogger['levelInfo'] = localLevelInfo;
    aLogger['levelWarning'] = localLevelWarning;
    aLogger['levelError'] = localLevelError;

    aLogger['VERSION'] = cVersion;

    return aLogger;
});
