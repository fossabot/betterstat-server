package io.github.incplusplus.thermostat.web.controller;

import io.github.incplusplus.thermostat.persistence.model.Client;
import io.github.incplusplus.thermostat.persistence.model.Privilege;
import io.github.incplusplus.thermostat.persistence.model.VerificationToken;
import io.github.incplusplus.thermostat.registration.OnRegistrationCompleteEvent;
import io.github.incplusplus.thermostat.security.SecurityClientService;
import io.github.incplusplus.thermostat.service.ClientService;
import io.github.incplusplus.thermostat.web.dto.ClientDto;
import io.github.incplusplus.thermostat.web.dto.PasswordDto;
import io.github.incplusplus.thermostat.web.error.InvalidOldPasswordException;
import io.github.incplusplus.thermostat.web.util.GenericResponse;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.MessageSource;
import org.springframework.core.env.Environment;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.io.UnsupportedEncodingException;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

//@EnableAutoConfiguration
@Controller
public class RegistrationController {
	private final Logger LOGGER = LoggerFactory.getLogger(getClass());
	
	@Autowired
	private ClientService clientService;
	
	@Autowired
	private SecurityClientService securityUserService;
	
	@Autowired
	private MessageSource messages;
	
	@Autowired
	private JavaMailSender mailSender;
	
	@Autowired
	private ApplicationEventPublisher eventPublisher;
	
	@Autowired
	private Environment env;
	
	@Autowired
	private AuthenticationManager authenticationManager;
	
	public RegistrationController() {
		super();
	}
	
	// Registration
	
	@PostMapping(value = "/user/registration")
	@ResponseBody
	public GenericResponse registerUserAccount(@RequestBody @Valid final ClientDto accountDto, final HttpServletRequest request) {
		LOGGER.debug("Registering user account with information: {}", accountDto);
		
		final Client registered = clientService.registerNewUserAccount(accountDto);
		eventPublisher.publishEvent(new OnRegistrationCompleteEvent(registered, request.getLocale(), getAppUrl(request)));
		return new GenericResponse("success");
	}
	
	@RequestMapping(value = "/registrationConfirm", method = RequestMethod.GET)
	public String confirmRegistration(final HttpServletRequest request, final Model model, @RequestParam("token") final String token) throws UnsupportedEncodingException
	{
		Locale locale = request.getLocale();
		final String result = clientService.validateVerificationToken(token);
		if (result.equals("valid")) {
			final Client client = clientService.getClient(token);
			// if (client.isUsing2FA()) {
			// model.addAttribute("qr", userService.generateQRUrl(client));
			// return "redirect:/qrcode.html?lang=" + locale.getLanguage();
			// }
			authWithoutPassword(client);
			model.addAttribute("message", messages.getMessage("message.accountVerified", null, locale));
			return "redirect:/console.html?lang=" + locale.getLanguage();
		}
		
		model.addAttribute("message", messages.getMessage("auth.message." + result, null, locale));
		model.addAttribute("expired", "expired".equals(result));
		model.addAttribute("token", token);
		return "redirect:/badUser.html?lang=" + locale.getLanguage();
	}
	
	// user activation - verification
	
	@RequestMapping(value = "/user/resendRegistrationToken", method = RequestMethod.GET)
	@ResponseBody
	public GenericResponse resendRegistrationToken(final HttpServletRequest request, @RequestParam("token") final String existingToken) {
		final VerificationToken newToken = clientService.generateNewVerificationToken(existingToken);
		final Client client = clientService.getClient(newToken.getToken());
		mailSender.send(constructResendVerificationTokenEmail(getAppUrl(request), request.getLocale(), newToken, client));
		return new GenericResponse(messages.getMessage("message.resendToken", null, request.getLocale()));
	}
	
	// Reset password
	@RequestMapping(value = "/user/resetPassword", method = RequestMethod.POST)
	@ResponseBody
	public GenericResponse resetPassword(final HttpServletRequest request, @RequestParam("email") final String userEmail) {
		final Client client = clientService.findUserByEmail(userEmail);
		if (client != null) {
			final String token = UUID.randomUUID().toString();
			clientService.createPasswordResetTokenForUser(client, token);
			mailSender.send(constructResetTokenEmail(getAppUrl(request), request.getLocale(), token, client));
		}
		return new GenericResponse(messages.getMessage("message.resetPasswordEmail", null, request.getLocale()));
	}
	
	@RequestMapping(value = "/user/changePassword", method = RequestMethod.GET)
	public String showChangePasswordPage(final Locale locale, final Model model, @RequestParam("id") final ObjectId id, @RequestParam("token") final String token) {
		final String result = securityUserService.validatePasswordResetToken(id, token);
		if (result != null) {
			model.addAttribute("message", messages.getMessage("auth.message." + result, null, locale));
			return "redirect:/login?lang=" + locale.getLanguage();
		}
		return "redirect:/updatePassword.html?lang=" + locale.getLanguage();
	}
	
	@RequestMapping(value = "/user/savePassword", method = RequestMethod.POST)
	@ResponseBody
	public GenericResponse savePassword(final Locale locale, @Valid PasswordDto passwordDto) {
		final Client client = (Client) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
		clientService.changeUserPassword(client, passwordDto.getNewPassword());
		return new GenericResponse(messages.getMessage("message.resetPasswordSuc", null, locale));
	}
	
	// change user password
	@RequestMapping(value = "/user/updatePassword", method = RequestMethod.POST)
	@ResponseBody
	public GenericResponse changeUserPassword(final Locale locale, @RequestBody @Valid PasswordDto passwordDto) {
		final Client client = clientService.findUserByEmail(((Client) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getEmail());
		if (!clientService.checkIfValidOldPassword(client, passwordDto.getOldPassword())) {
			throw new InvalidOldPasswordException();
		}
		clientService.changeUserPassword(client, passwordDto.getNewPassword());
		return new GenericResponse(messages.getMessage("message.updatePasswordSuc", null, locale));
	}
	
//	@RequestMapping(value = "/user/update/2fa", method = RequestMethod.POST)
//	@ResponseBody
//	public GenericResponse modifyUser2FA(@RequestParam("use2FA") final boolean use2FA) throws UnsupportedEncodingException
//	{
//		final Client client = clientService.updateUser2FA(use2FA);
//		if (use2FA) {
//			return new GenericResponse(clientService.generateQRUrl(client));
//		}
//		return null;
//	}
	
	// ============== NON-API ============
	
	private SimpleMailMessage constructResendVerificationTokenEmail(final String contextPath, final Locale locale, final VerificationToken newToken, final Client client) {
		final String confirmationUrl = contextPath + "/registrationConfirm.html?token=" + newToken.getToken();
		final String message = messages.getMessage("message.resendToken", null, locale);
		return constructEmail("Resend Registration Token", message + " \r\n" + confirmationUrl, client);
	}
	
	private SimpleMailMessage constructResetTokenEmail(final String contextPath, final Locale locale, final String token, final Client client) {
		final String url = contextPath + "/user/changePassword?id=" + client.get_id() + "&token=" + token;
		final String message = messages.getMessage("message.resetPassword", null, locale);
		return constructEmail("Reset Password", message + " \r\n" + url, client);
	}
	
	private SimpleMailMessage constructEmail(String subject, String body, Client client) {
		final SimpleMailMessage email = new SimpleMailMessage();
		email.setSubject(subject);
		email.setText(body);
		email.setTo(client.getEmail());
		email.setFrom(env.getProperty("support.email"));
		return email;
	}
	
	private String getAppUrl(HttpServletRequest request) {
		return "http://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath();
	}
	
	public void authWithHttpServletRequest(HttpServletRequest request, String username, String password) {
		try {
			request.login(username, password);
		} catch (ServletException e) {
			LOGGER.error("Error while login ", e);
		}
	}
	
	public void authWithAuthManager(HttpServletRequest request, String username, String password) {
		UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(username, password);
		authToken.setDetails(new WebAuthenticationDetails(request));
		Authentication authentication = authenticationManager.authenticate(authToken);
		SecurityContextHolder.getContext().setAuthentication(authentication);
		// request.getSession().setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, SecurityContextHolder.getContext());
	}
	
	public void authWithoutPassword(Client client) {
		//TODO Throws NPE if client doesn't have any roles
		Authentication authentication = null;
		try
		{
			List<Privilege> privileges = client.getRoles().stream().map(role -> role.getPrivileges()).flatMap(list -> list.stream()).distinct().collect(Collectors.toList());
			List<GrantedAuthority> authorities = privileges.stream().map(p -> new SimpleGrantedAuthority(p.getName())).collect(Collectors.toList());
			authentication = new UsernamePasswordAuthenticationToken(client, null, authorities);
		}
		catch (NullPointerException e)
		{
			List<GrantedAuthority> authorities = Collections.emptyList();
			authentication = new UsernamePasswordAuthenticationToken(client, null, authorities);
		}
		SecurityContextHolder.getContext().setAuthentication(authentication);
	}
}
