package io.github.incplusplus.thermostat.service;

import io.github.incplusplus.thermostat.persistence.model.Client;
import org.bson.types.ObjectId;

import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Optional;

public interface UserService
{
	
	Client registerNewUserAccount(UserDto accountDto) throws UserAlreadyExistException;
	
	Client getUser(String verificationToken);
	
	void saveRegisteredUser(Client client);
	
	void deleteUser(Client client);
	
	void createVerificationTokenForUser(Client client, String token);
	
	VerificationToken getVerificationToken(String VerificationToken);
	
	VerificationToken generateNewVerificationToken(String token);
	
	void createPasswordResetTokenForUser(Client client, String token);
	
	Client findUserByEmail(String email);
	
	PasswordResetToken getPasswordResetToken(String token);
	
	Client getUserByPasswordResetToken(String token);
	
	Optional<Client> getUserByID(ObjectId id);
	
	void changeUserPassword(Client client, String password);
	
	boolean checkIfValidOldPassword(Client client, String password);
	
	String validateVerificationToken(String token);
	
	String generateQRUrl(Client client) throws UnsupportedEncodingException;
	
	Client updateUser2FA(boolean use2FA);
	
	List<String> getUsersFromSessionRegistry();
	
}
