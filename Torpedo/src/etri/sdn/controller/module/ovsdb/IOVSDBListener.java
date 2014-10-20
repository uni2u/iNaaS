package etri.sdn.controller.module.ovsdb;

public interface IOVSDBListener {

	/**
	* Fired when an OVS switch is connected to the controller
	* @param ovsdb the new IOVSDB object
	*/
	public void addedSwitch(IOVSDB ovsdb);
	/**
	* Fired when an OVS switch is removed from the controller
	* @param ovsdb the new IOVSDB object
	*/
	public void removedSwitch(IOVSDB ovsdb);
	/**
	* Fired when a potential KVM based OVS switch connects
	*/
	public void addedNonVTASwitch();
}
