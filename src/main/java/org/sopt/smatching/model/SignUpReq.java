package org.sopt.smatching.model;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Getter
@Setter
@NoArgsConstructor
public class SignUpReq {

    private String nickname;
    private String email;
    private String password;
}
