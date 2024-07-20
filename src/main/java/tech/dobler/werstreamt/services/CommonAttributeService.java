package tech.dobler.werstreamt.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.ui.Model;

@Service
@RequiredArgsConstructor
public class CommonAttributeService {
    private final ImdbEntryRepository imdbEntryRepository;
    public void add(Model model)
    {
        model.addAttribute("selectedList", imdbEntryRepository.getNameOfList());
    }
}
