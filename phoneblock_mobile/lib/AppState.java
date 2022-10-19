public interface AppState extends de.haumacher.msgbuf.data.DataObject, de.haumacher.msgbuf.binary.BinaryDataObject, de.haumacher.msgbuf.observer.Observable {

	/**
	 * Creates a {@link AppState} instance.
	 */
	static AppState create() {
