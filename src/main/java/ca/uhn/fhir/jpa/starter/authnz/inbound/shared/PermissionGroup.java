package ca.uhn.fhir.jpa.starter.authnz.inbound.shared;

import java.util.List;

public class PermissionGroup {
	private Permission name;
	private List<String> arguments;

	public PermissionGroup() {
	}

	public PermissionGroup(Permission name, List<String> arguments) {
		this.name = name;
		this.arguments = arguments;
	}

	public Permission getName() {
		return name;
	}

	public void setName(Permission name) {
		this.name = name;
	}

	public List<String> getArguments() {
		return arguments;
	}

	public void setArguments(List<String> arguments) {
		this.arguments = arguments;
	}
}
